package dev.zymekoh.optimatch.transform;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Turns the raw mixin census into the switches a player can move.
 *
 * <p>Two shapes of cooperation exist in the wild and both are supported. Sodium publishes package
 * rules read from {@code sodium-mixins.properties}, so every distinct mixin package becomes a knob.
 * ImmediatelyFast publishes booleans in its own JSON and its plugin maps those to mixins internally,
 * so each documented option becomes a knob. Mods that ship no {@code IMixinConfigPlugin} publish
 * nothing, and are reported as locked rather than quietly omitted.
 */
public final class KnobRegistry {
	private static final String SODIUM_ROOT = "net.caffeinemc.mods.sodium.mixin.";

	/** Human wording for the package paths worth explaining; longest prefix wins. */
	private static final Map<String, String> SODIUM_GLOSSARY = new LinkedHashMap<>();

	static {
		SODIUM_GLOSSARY.put("core", "El motor de Sodium. Sin esto el mod no hace nada.");
		SODIUM_GLOSSARY.put("features.render.entity.cull",
			"Descarta entidades fuera de vista antes de procesarlas. Menos trabajo por frame.");
		SODIUM_GLOSSARY.put("features.render.entity.shadows",
			"Las sombras ovaladas bajo las entidades.");
		SODIUM_GLOSSARY.put("features.render.entity", "Dibujado de entidades.");
		SODIUM_GLOSSARY.put("features.render.world.clouds", "Las nubes del cielo.");
		SODIUM_GLOSSARY.put("features.render.world.sky", "El cielo, el sol y la luna.");
		SODIUM_GLOSSARY.put("features.render.particle", "Las particulas del mundo.");
		SODIUM_GLOSSARY.put("features.render.gui.font", "El texto del HUD y los menus.");
		SODIUM_GLOSSARY.put("features.render.immediate.buffer_builder.sorting",
			"Ordena las caras translucidas cada frame. Quitarlo ahorra CPU, pero el agua y el cristal se ven mal.");
		SODIUM_GLOSSARY.put("features.render.immediate.buffer_builder.intrinsics",
			"Copia de vertices acelerada.");
		SODIUM_GLOSSARY.put("features.render.immediate.matrix_stack", "Pila de matrices acelerada.");
		SODIUM_GLOSSARY.put("features.render.immediate", "Dibujado inmediato: HUD, entidades, items.");
		SODIUM_GLOSSARY.put("features.render.viewport", "Calculo del frustum de la camara.");
		SODIUM_GLOSSARY.put("features.textures.animations.tracking",
			"Solo anima las texturas visibles. Un ahorro grande y sin efecto visual.");
		SODIUM_GLOSSARY.put("features.textures", "Gestion del atlas de texturas.");
		SODIUM_GLOSSARY.put("features.gui", "Pantallas de opciones que anade Sodium.");
		SODIUM_GLOSSARY.put("workarounds", "Parches para drivers y sistemas concretos. No los toques sin motivo.");
	}

	/** Labels and wording for the options ImmediatelyFast publishes in its own config. */
	private static final Map<String, String[]> IF_OPTIONS = new LinkedHashMap<>();

	static {
		IF_OPTIONS.put("enhanced_batching", new String[]{"Agrupado de dibujado",
			"Junta las llamadas de dibujado del HUD y el texto para mandarlas de golpe. Sube FPS, pero al agrupar retrasa lo que ves."});
		IF_OPTIONS.put("font_atlas_resizing", new String[]{"Atlas de fuente elastico",
			"Redimensiona el atlas de texto en vez de rehacerlo."});
		IF_OPTIONS.put("map_atlas_generation", new String[]{"Atlas de mapas",
			"Junta las texturas de los mapas en un solo atlas."});
		IF_OPTIONS.put("skip_text_translucency_sorting", new String[]{"Saltar orden de texto translucido",
			"Evita reordenar el texto por profundidad. Menos trabajo por frame."});
		IF_OPTIONS.put("fast_text_lookup", new String[]{"Busqueda rapida de glifos",
			"Cachea la busqueda de caracteres al dibujar texto."});
		IF_OPTIONS.put("avoid_redundant_framebuffer_switching", new String[]{"Evitar cambios de framebuffer",
			"No cambia el destino de render si ya estaba activo."});
		IF_OPTIONS.put("fix_slow_buffer_upload_on_apple_gpu", new String[]{"Arreglo para GPU de Apple",
			"Solo tiene efecto en GPU de Apple."});
		IF_OPTIONS.put("experimental_sign_text_buffering", new String[]{"Buffer de texto de carteles",
			"Experimental: cachea el texto de los carteles. Puede dar fallos visuales."});
		IF_OPTIONS.put("experimental_disable_resource_pack_conflict_handling",
			new String[]{"Sin control de conflictos de packs",
				"Experimental: deja de detectar packs de recursos incompatibles."});
	}

	private static volatile List<MixinKnob> cache;

	private KnobRegistry() {
	}

	public static List<MixinKnob> knobs() {
		List<MixinKnob> built = cache;
		if (built == null) {
			built = build();
			cache = built;
		}
		return built;
	}

	public static void invalidate() {
		cache = null;
	}

	public static MixinKnob byKey(String key) {
		for (MixinKnob knob : knobs()) {
			if (knob.key().equals(key)) {
				return knob;
			}
		}
		return null;
	}

	/** Configs whose owner published no opt-out hook: visible, explained, but not switchable. */
	public static List<MixinInventory.Config> locked() {
		List<MixinInventory.Config> out = new ArrayList<>();
		for (MixinInventory.Config config : MixinInventory.configs()) {
			if (!config.cooperative() && !config.mixins().isEmpty()) {
				out.add(config);
			}
		}
		out.sort((a, b) -> Integer.compare(b.mixins().size(), a.mixins().size()));
		return out;
	}

	private static List<MixinKnob> build() {
		List<MixinKnob> out = new ArrayList<>();
		out.addAll(buildSodium());
		out.addAll(buildImmediatelyFast());
		return List.copyOf(out);
	}

	private static List<MixinKnob> buildSodium() {
		if (!FabricLoader.getInstance().isModLoaded("sodium")) {
			return List.of();
		}
		// Group every Sodium mixin by its package, which is exactly the granularity its rules use.
		Map<String, List<MixinInventory.Mixin>> byPackage = new TreeMap<>();
		for (MixinInventory.Config config : MixinInventory.configs()) {
			if (!config.cooperative() || !config.name().startsWith("sodium")) {
				continue;
			}
			for (MixinInventory.Mixin mixin : config.mixins()) {
				String full = mixin.mixinClass();
				if (!full.startsWith(SODIUM_ROOT)) {
					continue;
				}
				String path = full.substring(SODIUM_ROOT.length());
				int lastDot = path.lastIndexOf('.');
				if (lastDot <= 0) {
					continue;
				}
				byPackage.computeIfAbsent(path.substring(0, lastDot), key -> new ArrayList<>()).add(mixin);
			}
		}

		List<MixinKnob> out = new ArrayList<>();
		for (Map.Entry<String, List<MixinInventory.Mixin>> entry : byPackage.entrySet()) {
			String path = entry.getKey();
			List<String> classes = new ArrayList<>();
			Set<String> targets = new LinkedHashSet<>();
			for (MixinInventory.Mixin mixin : entry.getValue()) {
				classes.add(mixin.mixinClass());
				targets.addAll(mixin.targets());
			}
			out.add(new MixinKnob("sodium", "mixin." + path, prettify(path), describeSodium(path),
				MixinKnob.Kind.RULE, true, riskFor("sodium", path, targets),
				List.copyOf(classes), List.copyOf(targets)));
		}
		return out;
	}

	private static List<MixinKnob> buildImmediatelyFast() {
		if (!FabricLoader.getInstance().isModLoaded("immediatelyfast")) {
			return List.of();
		}
		Set<String> targets = new LinkedHashSet<>();
		for (MixinInventory.Config config : MixinInventory.configs()) {
			if (config.name().contains("immediatelyfast")) {
				for (MixinInventory.Mixin mixin : config.mixins()) {
					targets.addAll(mixin.targets());
				}
			}
		}

		JsonObject json = readJson(KnobStore.configDir().resolve("immediatelyfast.json"));
		List<MixinKnob> out = new ArrayList<>();
		for (Map.Entry<String, String[]> option : IF_OPTIONS.entrySet()) {
			String id = option.getKey();
			// Only offer what this build of the mod actually understands.
			if (json == null || !json.has(id) || !json.get(id).isJsonPrimitive()
				|| !json.get(id).getAsJsonPrimitive().isBoolean()) {
				continue;
			}
			MixinKnob.Risk risk = id.startsWith("experimental_") || id.equals("enhanced_batching")
				? MixinKnob.Risk.SHARED
				: MixinKnob.Risk.SAFE;
			out.add(new MixinKnob("immediatelyfast", id, option.getValue()[0], option.getValue()[1],
				MixinKnob.Kind.FLAG, json.get(id).getAsBoolean(), risk, List.of(), List.copyOf(targets)));
		}
		return out;
	}

	/**
	 * A knob counts as safe only when nobody else injects into the same classes. When another mod
	 * does, switching it off changes what that other mod sees, and that is the case worth a review.
	 */
	private static MixinKnob.Risk riskFor(String modId, String path, Set<String> targets) {
		if (path.startsWith("core") || path.startsWith("workarounds")) {
			return MixinKnob.Risk.CORE;
		}
		for (String target : targets) {
			for (MixinInventory.Mixin other : MixinInventory.touching(target)) {
				if (!ownerOf(other.configName()).equals(modId)) {
					return MixinKnob.Risk.SHARED;
				}
			}
		}
		return MixinKnob.Risk.SAFE;
	}

	/** Best-effort mod id for a config file name, used only to tell one owner from another. */
	public static String ownerOf(String configName) {
		String base = configName.replace(".mixins.json", "").replace(".json", "")
			.replace("mixins.", "");
		int dash = base.indexOf('-');
		String head = dash > 0 ? base.substring(0, dash) : base;
		for (var container : FabricLoader.getInstance().getAllMods()) {
			String id = container.getMetadata().getId();
			if (id.equals(head) || base.startsWith(id) || id.startsWith(head)) {
				return id;
			}
		}
		return head;
	}

	/** Which other mods inject into the same classes as this knob. */
	public static List<String> neighboursOf(MixinKnob knob) {
		Set<String> others = new LinkedHashSet<>();
		for (String target : knob.targets()) {
			for (MixinInventory.Mixin mixin : MixinInventory.touching(target)) {
				String owner = ownerOf(mixin.configName());
				if (!owner.equals(knob.modId())) {
					others.add(owner);
				}
			}
		}
		return List.copyOf(others);
	}

	private static String describeSodium(String path) {
		String best = null;
		for (Map.Entry<String, String> entry : SODIUM_GLOSSARY.entrySet()) {
			if (path.startsWith(entry.getKey())
				&& (best == null || entry.getKey().length() > best.length())) {
				best = entry.getKey();
			}
		}
		return best == null ? "Grupo de parches de Sodium en " + path + "." : SODIUM_GLOSSARY.get(best);
	}

	private static String prettify(String path) {
		String tail = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
		String spaced = tail.replace('_', ' ');
		return spaced.isEmpty() ? path : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static JsonObject readJson(Path file) {
		try {
			if (!Files.exists(file)) {
				return null;
			}
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}
}

package dev.zymekoh.optimatch.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

/**
 * Enumerates every mod the loader knows about and re-reads each {@code fabric.mod.json} so we can
 * see the declared mixin configs. The loader's public metadata API deliberately hides those, and we
 * need them to find which mods fight over the same target classes.
 */
public final class ModScanner {
	private static final Set<String> BUILTIN_IDS = Set.of("minecraft", "java", "fabricloader", "fabric-loader");

	private ModScanner() {
	}

	public static List<InstalledMod> scan() {
		List<InstalledMod> result = new ArrayList<>();

		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			try {
				result.add(read(container));
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.warn("Could not inspect mod {}", container.getMetadata().getId(), exception);
			}
		}

		result.sort(Comparator.comparing(InstalledMod::displayName, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(result);
	}

	private static InstalledMod read(ModContainer container) {
		ModMetadata metadata = container.getMetadata();
		String id = metadata.getId();

		String authors = metadata.getAuthors().stream()
			.map(Person::getName)
			.filter(name -> name != null && !name.isBlank())
			.collect(Collectors.joining(", "));

		List<String> dependencies = metadata.getDependencies().stream()
			.filter(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS)
			.map(ModDependency::getModId)
			.filter(dependencyId -> !BUILTIN_IDS.contains(dependencyId))
			.sorted()
			.toList();

		return new InstalledMod(
			id,
			clean(metadata.getName()),
			metadata.getVersion().getFriendlyString(),
			clean(metadata.getDescription()),
			clean(authors),
			metadata.getIconPath(32).orElse(""),
			List.copyOf(container.getRootPaths()),
			readMixinConfigs(container),
			dependencies,
			classify(container, metadata),
			sourceFileName(container)
		);
	}

	/**
	 * Reads the {@code mixins} array out of the mod's own {@code fabric.mod.json}. Entries are either
	 * a plain string or an object with a {@code config} field plus an {@code environment} filter.
	 */
	private static List<String> readMixinConfigs(ModContainer container) {
		Optional<Path> descriptor = container.findPath("fabric.mod.json");
		if (descriptor.isEmpty()) {
			return List.of();
		}

		try (BufferedReader reader = Files.newBufferedReader(descriptor.get(), StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return List.of();
			}
			JsonElement mixins = parsed.getAsJsonObject().get("mixins");
			if (mixins == null || !mixins.isJsonArray()) {
				return List.of();
			}

			List<String> configs = new ArrayList<>();
			JsonArray array = mixins.getAsJsonArray();
			for (JsonElement entry : array) {
				if (entry.isJsonPrimitive()) {
					configs.add(entry.getAsString());
				} else if (entry.isJsonObject()) {
					JsonObject object = entry.getAsJsonObject();
					JsonElement config = object.get("config");
					if (config != null && config.isJsonPrimitive()) {
						// Skip server-only configs: they never load on a client instance.
						JsonElement environment = object.get("environment");
						String env = environment != null && environment.isJsonPrimitive()
							? environment.getAsString().toLowerCase(Locale.ROOT)
							: "*";
						if (!"server".equals(env)) {
							configs.add(config.getAsString());
						}
					}
				}
			}
			return List.copyOf(configs);
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Could not read fabric.mod.json for {}", container.getMetadata().getId(), exception);
			return List.of();
		}
	}

	private static InstalledMod.Kind classify(ModContainer container, ModMetadata metadata) {
		String id = metadata.getId();
		if (BUILTIN_IDS.contains(id) || "builtin".equals(metadata.getType())) {
			return InstalledMod.Kind.BUILTIN;
		}
		if (id.equals("fabric-api") || id.startsWith("fabric-") && id.contains("-v")) {
			return InstalledMod.Kind.FABRIC_API;
		}
		if (container.getContainingMod().isPresent()) {
			return InstalledMod.Kind.NESTED;
		}
		return InstalledMod.Kind.USER;
	}

	private static String sourceFileName(ModContainer container) {
		List<Path> roots = container.getRootPaths();
		if (roots.isEmpty()) {
			return "";
		}
		// Root paths live inside a jar filesystem, so the jar itself is the filesystem's backing file.
		Path root = roots.get(0);
		String raw = root.getFileSystem().toString();
		int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
		String name = slash >= 0 && slash + 1 < raw.length() ? raw.substring(slash + 1) : raw;
		return name.endsWith(".jar") ? name : "";
	}

	private static String clean(String value) {
		return value == null ? "" : value.replaceAll("\s+", " ").strip();
	}
}

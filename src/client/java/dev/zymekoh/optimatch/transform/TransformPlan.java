package dev.zymekoh.optimatch.transform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What a preset would actually do here, worked out before anything is written.
 *
 * <p>Presets are written against mods in general; a plan is that preset met with this instance. It
 * separates three things the player needs to keep apart: the changes that will happen, the ones that
 * cannot happen because the mod is absent or refuses, and the ones that will happen but deserve a
 * look first because another mod is injecting into the same classes.
 */
public record TransformPlan(TransformPreset preset, List<Change> changes, List<Concern> concerns) {

	public record Change(MixinKnob knob, boolean from, boolean to) {
		public String summary() {
			return this.knob.label() + ": " + (this.from ? "si" : "no") + " a " + (this.to ? "si" : "no");
		}
	}

	public record Concern(Severity severity, String title, String detail) {
	}

	public enum Severity {
		/** Cannot be done at all: the mod is not here, or it does not publish this switch. */
		BLOCKING,
		/** Will be done, but it reaches into classes another mod is also using. */
		SERIOUS,
		/** Worth knowing, not worth stopping for. */
		NOTE
	}

	public boolean isEmpty() {
		return this.changes.isEmpty();
	}

	/** Only a blocking or serious finding earns a modal; notes are shown inline in the tab. */
	public boolean needsReview() {
		return this.concerns.stream().anyMatch(c -> c.severity() != Severity.NOTE);
	}

	public long count(Severity severity) {
		return this.concerns.stream().filter(c -> c.severity() == severity).count();
	}

	/** The changes that are actually applicable, i.e. everything not sunk by a blocking concern. */
	public List<Change> applicable() {
		return this.changes;
	}

	public static TransformPlan build(TransformPreset preset) {
		List<Change> changes = new ArrayList<>();
		List<Concern> concerns = new ArrayList<>();

		if (preset.restoresDefaults()) {
			return buildRestore(preset);
		}

		for (Map.Entry<String, Boolean> wanted : preset.desired().entrySet()) {
			String key = wanted.getKey();
			MixinKnob knob = KnobRegistry.byKey(key);
			if (knob == null) {
				concerns.add(unavailable(key));
				continue;
			}

			boolean current = KnobStore.read(knob);
			if (current == wanted.getValue()) {
				continue;
			}

			Path file = KnobStore.fileFor(knob);
			if (Files.exists(file) && !Files.isWritable(file)) {
				concerns.add(new Concern(Severity.BLOCKING, "Archivo protegido",
					file.getFileName() + " es de solo lectura, asi que " + knob.label()
						+ " no se puede cambiar."));
				continue;
			}

			changes.add(new Change(knob, current, wanted.getValue()));
			concerns.addAll(risksOf(knob, wanted.getValue()));
		}

		if (changes.isEmpty() && concerns.isEmpty()) {
			concerns.add(new Concern(Severity.NOTE, "Ya lo tienes asi",
				"Tu configuracion actual ya coincide con este preset. No hay nada que cambiar."));
		}
		return new TransformPlan(preset, List.copyOf(changes), List.copyOf(concerns));
	}

	private static TransformPlan buildRestore(TransformPreset preset) {
		List<Change> changes = new ArrayList<>();
		for (MixinKnob knob : KnobRegistry.knobs()) {
			if (KnobStore.isOverridden(knob)) {
				changes.add(new Change(knob, KnobStore.read(knob), knob.defaultOn()));
			}
		}
		List<Concern> concerns = changes.isEmpty()
			? List.of(new Concern(Severity.NOTE, "Nada que restaurar",
				"No has cambiado ningun ajuste, asi que todo esta ya como lo dejo cada mod."))
			: List.of();
		return new TransformPlan(preset, List.copyOf(changes), concerns);
	}

	/** Explains a missing knob in terms of the reason rather than the key that failed to resolve. */
	private static Concern unavailable(String key) {
		String modId = key.substring(0, Math.max(0, key.indexOf(':')));
		boolean installed = FabricLoader.getInstance().isModLoaded(modId);
		if (!installed) {
			return new Concern(Severity.BLOCKING, "Falta " + modId,
				"Este preset ajusta " + modId + ", y no lo tienes instalado. El resto del preset si se aplica.");
		}
		return new Concern(Severity.BLOCKING, modId + " no publica ese ajuste",
			"Tienes " + modId + ", pero esta version no ofrece " + key.substring(key.indexOf(':') + 1)
				+ ". Puede que lo hayan renombrado o retirado.");
	}

	/**
	 * The genuinely worrying case: turning something off underneath a mod that is still injecting
	 * into the same classes and has no idea the ground moved.
	 */
	private static List<Concern> risksOf(MixinKnob knob, boolean turningOn) {
		if (turningOn) {
			// Restoring a mod to doing more of its own job is not the dangerous direction.
			return List.of();
		}
		List<Concern> out = new ArrayList<>();
		if (knob.risk() == MixinKnob.Risk.CORE) {
			out.add(new Concern(Severity.SERIOUS, "Es maquinaria interna de " + knob.modId(),
				knob.label() + " no es una funcion suelta: es parte del motor. Apagarlo puede dejar "
					+ knob.modId() + " a medias o impedir que arranque."));
			return out;
		}
		List<String> neighbours = KnobRegistry.neighboursOf(knob);
		if (knob.risk() == MixinKnob.Risk.SHARED && !neighbours.isEmpty()) {
			out.add(new Concern(Severity.SERIOUS, "Terreno compartido con " + String.join(", ", neighbours),
				"Estos mods inyectan en las mismas clases que " + knob.label() + ". Al apagarlo, "
					+ "seguiran ahi esperando un comportamiento que ya no ocurre."));
		}
		return out;
	}
}

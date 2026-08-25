package dev.zymekoh.optimatch.catalog;

import dev.zymekoh.optimatch.hardware.HardwareProfile;
import dev.zymekoh.optimatch.scan.InstalledMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a {@link Preset} plus the current machine and mod list into a concrete recommendation.
 *
 * <p>The preset decides <em>what kind</em> of mod qualifies; the hardware decides <em>how much each
 * one is worth here</em>. A 4-thread laptop with 8 GB and a 16-thread desktop with 64 GB ask for
 * genuinely different things, and every suggestion carries the hardware fact that earned it its
 * place so the player can see the reasoning rather than trust a black box.
 */
public final class PresetBuilder {
	/** Below this the JVM spends real time on garbage collection instead of frames. */
	private static final long LOW_RAM_MB = 8 * 1024;
	private static final long VERY_LOW_RAM_MB = 6 * 1024;

	/** Minecraft's main thread dominates, but below this the helper threads start fighting it. */
	private static final int LOW_THREAD_COUNT = 4;

	/** Above this refresh rate, latency work is perceptible rather than theoretical. */
	private static final int HIGH_REFRESH_HZ = 120;

	/** Only a comfortable machine should be spending frames on looks. */
	private static final int VISUALS_AFFORDABLE_SCORE = 65;

	private PresetBuilder() {
	}

	public static Recommendation build(Preset preset, HardwareProfile hardware, List<InstalledMod> installed) {
		Set<String> installedIds = new HashSet<>();
		for (InstalledMod mod : installed) {
			installedIds.add(mod.id());
		}

		List<Recommendation.Suggestion> toInstall = new ArrayList<>();
		List<Recommendation.Suggestion> alreadyHave = new ArrayList<>();

		for (CatalogEntry entry : Catalog.entries().values()) {
			if (!fits(preset, entry, hardware)) {
				continue;
			}
			Scored scored = score(preset, entry, hardware);
			Recommendation.Suggestion suggestion =
				new Recommendation.Suggestion(entry, scored.relevance(), scored.reason());

			if (isInstalled(entry, installedIds)) {
				alreadyHave.add(suggestion);
			} else {
				toInstall.add(suggestion);
			}
		}

		addMissingDependencies(toInstall, installedIds);

		// Biggest win for this specific machine first.
		Comparator<Recommendation.Suggestion> byRelevance =
			Comparator.comparingInt(Recommendation.Suggestion::relevance).reversed();
		toInstall.sort(byRelevance);
		alreadyHave.sort(byRelevance);

		return new Recommendation(preset, List.copyOf(toInstall), List.copyOf(alreadyHave),
			collectWarnings(preset, installed, hardware));
	}

	/** Whether an entry belongs in this preset on this machine at all. */
	private static boolean fits(Preset preset, CatalogEntry entry, HardwareProfile hardware) {
		if (entry.desktopOnly() && hardware.platform().isMobile()) {
			return false;
		}
		// Nvidium only does anything on NVIDIA hardware; anywhere else it is dead weight.
		if (entry.modId().equals("nvidium") && !isNvidia(hardware)) {
			return false;
		}
		// Support libraries ride along with their parent instead of being suggested alone.
		if (entry.primaryRole() == ModRole.LIBRARY && !entry.requires().isEmpty()) {
			return false;
		}

		return switch (preset) {
			case MAX_FPS -> entry.fpsImpact() >= 1
				// On a memory-starved machine, freeing RAM is a frame-rate fix too.
				|| (entry.hasRole(ModRole.MEMORY) && hardware.totalRamMb() < LOW_RAM_MB);

			// Anything that adds delay is disqualified outright, however many frames it buys.
			case MIN_LATENCY -> entry.latencyImpact() >= 1 && !entry.hurtsLatency();

			case VANILLA_ENHANCED -> {
				if (entry.hurtsLatency() || entry.fpsImpact() < 0) {
					// Looks are only worth it on a machine with frames to spare.
					yield entry.hasRole(ModRole.VISUAL)
						&& hardware.performanceScore() >= VISUALS_AFFORDABLE_SCORE
						&& !hardware.platform().isMobile();
				}
				yield entry.fpsImpact() >= 1 || entry.latencyImpact() >= 1 || entry.hasRole(ModRole.PVP);
			}
		};
	}

	private record Scored(int relevance, String reason) {
	}

	/**
	 * Weighs one entry against this machine. The reason returned is the single most relevant hardware
	 * fact, because a list where everything says "mejora el rendimiento" tells the player nothing.
	 */
	private static Scored score(Preset preset, CatalogEntry entry, HardwareProfile hardware) {
		int base = switch (preset) {
			case MAX_FPS -> entry.fpsImpact() * 18 + entry.latencyImpact() * 4;
			case MIN_LATENCY -> entry.latencyImpact() * 18 + entry.fpsImpact() * 4;
			case VANILLA_ENHANCED -> entry.fpsImpact() * 11 + entry.latencyImpact() * 11;
		};

		int bonus = 0;
		String reason = entry.primaryRole().explanation();

		if (entry.hasRole(ModRole.MEMORY)) {
			if (hardware.totalRamMb() < VERY_LOW_RAM_MB) {
				bonus += 34;
				reason = String.format("Solo %.1f GB de RAM: recortar memoria es lo que mas te va a ayudar",
					hardware.totalRamMb() / 1024.0);
			} else if (hardware.totalRamMb() < LOW_RAM_MB) {
				bonus += 20;
				reason = String.format("%.0f GB de RAM: te queda poco margen, esto lo alivia",
					hardware.totalRamMb() / 1024.0);
			}
		}

		if (entry.hasRole(ModRole.TICK) && hardware.logicalThreads() <= LOW_THREAD_COUNT) {
			bonus += 22;
			reason = hardware.logicalThreads() + " hilos: la logica del juego pesa mas en tu CPU";
		}

		if (entry.hasRole(ModRole.LATENCY) && hardware.refreshRateHz() >= HIGH_REFRESH_HZ) {
			bonus += 18;
			reason = "Pantalla de " + hardware.refreshRateHz() + " Hz: aqui si se nota bajar la latencia";
		}

		if (entry.hasRole(ModRole.FPS) && hardware.isLowEnd()) {
			bonus += 16;
			reason = "Equipo justo (" + hardware.performanceScore() + "/100): cada fotograma cuenta";
		}

		if (entry.modId().equals("nvidium") && isNvidia(hardware)) {
			bonus += 26;
			reason = "GPU NVIDIA detectada: puedes usar mesh shaders";
		}

		if (hardware.platform().isMobile() && (entry.hasRole(ModRole.MEMORY) || entry.hasRole(ModRole.FPS))) {
			bonus += 20;
			reason = "PojavLauncher: memoria y fotogramas son lo critico aqui";
		}

		if (entry.hasRole(ModRole.VISUAL) && hardware.performanceScore() >= VISUALS_AFFORDABLE_SCORE) {
			reason = "Equipo potente (" + hardware.performanceScore() + "/100): puedes permitirte esto";
		}

		return new Scored(Math.max(0, Math.min(100, base + bonus)), reason);
	}

	/** Pulls in anything a suggested mod needs but the player does not have. */
	private static void addMissingDependencies(List<Recommendation.Suggestion> toInstall, Set<String> installedIds) {
		List<Recommendation.Suggestion> dependencies = new ArrayList<>();

		for (Recommendation.Suggestion suggestion : toInstall) {
			for (String requiredId : suggestion.entry().requires()) {
				CatalogEntry required = Catalog.find(requiredId);
				if (required == null || isInstalled(required, installedIds)) {
					continue;
				}
				boolean alreadyQueued = toInstall.stream().anyMatch(s -> s.modId().equals(requiredId))
					|| dependencies.stream().anyMatch(s -> s.modId().equals(requiredId));
				if (!alreadyQueued) {
					// Slightly above its parent so a dependency never sorts below what needs it.
					dependencies.add(new Recommendation.Suggestion(required,
						Math.min(100, suggestion.relevance() + 1),
						"Necesario para " + suggestion.entry().name()));
				}
			}
		}
		toInstall.addAll(dependencies);
	}

	private static List<Recommendation.Warning> collectWarnings(Preset preset, List<InstalledMod> installed,
																HardwareProfile hardware) {
		List<Recommendation.Warning> warnings = new ArrayList<>();

		for (InstalledMod mod : installed) {
			CatalogEntry entry = Catalog.findInstalled(mod.id());
			if (entry == null) {
				continue;
			}

			if (preset == Preset.MIN_LATENCY && entry.hurtsLatency()) {
				warnings.add(new Recommendation.Warning(mod.id(), entry.name(),
					"Anade retardo a la interfaz. Desactivalo si buscas latencia minima."));
			}
			if (preset == Preset.MAX_FPS && entry.fpsImpact() < 0) {
				warnings.add(new Recommendation.Warning(mod.id(), entry.name(),
					"Cuesta FPS. Quitalo si quieres el maximo rendimiento."));
			}
			if (preset == Preset.VANILLA_ENHANCED && entry.hurtsLatency()) {
				warnings.add(new Recommendation.Warning(mod.id(), entry.name(),
					"Penaliza la respuesta en PvP."));
			}
			// Hardware-specific: a heavy visual mod on a machine that cannot carry it.
			if (entry.hasRole(ModRole.VISUAL) && entry.fpsImpact() < 0 && hardware.isLowEnd()) {
				warnings.add(new Recommendation.Warning(mod.id(), entry.name(),
					"Tu equipo esta en " + hardware.performanceScore() + "/100; esto le cuesta caro."));
			}
			if (entry.desktopOnly() && hardware.platform().isMobile()) {
				warnings.add(new Recommendation.Warning(mod.id(), entry.name(),
					"No funciona en PojavLauncher."));
			}
		}
		return List.copyOf(warnings);
	}

	/** Slug and Fabric mod id often differ, so every installed id is tested against both. */
	private static boolean isInstalled(CatalogEntry entry, Set<String> installedIds) {
		for (String installedId : installedIds) {
			if (entry.matches(installedId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isNvidia(HardwareProfile hardware) {
		String haystack = (hardware.gpuName() + " " + hardware.gpuVendor()).toLowerCase(Locale.ROOT);
		return haystack.contains("nvidia") || haystack.contains("geforce") || haystack.contains("rtx");
	}
}

package dev.zymekoh.optimatch.scan;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.catalog.Catalog;
import dev.zymekoh.optimatch.catalog.CatalogEntry;
import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.hardware.HardwareProfile;
import dev.zymekoh.optimatch.hardware.HardwareScanner;
import java.util.ArrayList;
import java.util.Comparator;
import dev.zymekoh.optimatch.transform.KnobRegistry;
import dev.zymekoh.optimatch.transform.MixinInventory;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A health report for the instance: the things that are quietly wrong and would otherwise only show
 * up as "the mod does nothing" or "my game is slow".
 *
 * <p>The headline check is one nothing else offers. A mixin names the method it patches as a string,
 * resolved at load time; if Minecraft renamed or removed that method, the injection simply never
 * applies. The mod loads, reports no error, and silently does less than it claims. Reading the real
 * class ({@link TargetInspector}) makes that visible.
 */
public final class HealthCheck {
	/** Below this the JVM spends noticeable time collecting garbage instead of drawing frames. */
	private static final long LOW_HEAP_MB = 3072;
	/** Handing the JVM most of the machine starves the OS and the GPU driver. */
	private static final float GREEDY_HEAP_SHARE = 0.6F;

	private HealthCheck() {
	}

	public enum Severity {
		CRITICAL, WARNING, INFO, GOOD
	}

	/**
	 * One thing worth telling the player.
	 *
	 * @param modId  the mod it concerns, empty for instance-wide findings
	 * @param action what they can actually do about it; empty when nothing is needed
	 */
	public record Finding(Severity severity, String title, String detail, String modId, String action) {
	}

	/** The finished report. */
	public record Report(List<Finding> findings, int modsChecked, int injectionsChecked, boolean updatesChecked) {
		public long count(Severity severity) {
			return this.findings.stream().filter(finding -> finding.severity() == severity).count();
		}

		public boolean isHealthy() {
			return count(Severity.CRITICAL) == 0 && count(Severity.WARNING) == 0;
		}
	}

	/**
	 * Runs every check. Heavy: reads jars and talks to Modrinth, so it belongs off the render thread.
	 *
	 * @param checkUpdates whether to ask Modrinth for newer versions; skipped when offline
	 */
	public static Report run(boolean checkUpdates) {
		List<InstalledMod> mods = ModScanner.scan();
		List<InstalledMod> userMods = mods.stream().filter(InstalledMod::isUserFacing).toList();
		List<MixinTarget> targets = MixinScanner.scan(mods);

		List<Finding> findings = new ArrayList<>();
		findings.addAll(brokenMixins(targets, mods));
		findings.addAll(memoryAdvice());
		findings.addAll(redundantMods(userMods));
		findings.addAll(mixinPressure());

		boolean updatesChecked = false;
		if (checkUpdates) {
			List<Finding> updates = outdatedMods(userMods);
			updatesChecked = true;
			findings.addAll(updates);
		}

		if (findings.isEmpty()) {
			findings.add(new Finding(Severity.GOOD, "Todo en orden",
				"No se encontro ningun problema en tu instalacion.", "", ""));
		}

		findings.sort(Comparator.comparingInt(finding -> finding.severity().ordinal()));
		return new Report(List.copyOf(findings), userMods.size(), targets.size(), updatesChecked);
	}

	/**
	 * The class the most different mods are all writing into.
	 *
	 * <p>Reported as one finding rather than one per class. Plenty of classes carry injections from
	 * two mods and that is ordinary; naming every one of them would bury the single case that is
	 * actually crowded. This says where the pressure is highest and stops.
	 */
	private static List<Finding> mixinPressure() {
		List<MixinInventory.Config> configs = MixinInventory.configs();
		if (configs.isEmpty()) {
			return List.of();
		}

		Map<String, Set<String>> ownersByTarget = new LinkedHashMap<>();
		for (MixinInventory.Config config : configs) {
			String owner = KnobRegistry.ownerOf(config.name());
			for (MixinInventory.Mixin mixin : config.mixins()) {
				for (String target : mixin.targets()) {
					ownersByTarget.computeIfAbsent(target, key -> new LinkedHashSet<>()).add(owner);
				}
			}
		}

		String busiest = null;
		Set<String> busiestOwners = Set.of();
		for (Map.Entry<String, Set<String>> entry : ownersByTarget.entrySet()) {
			if (entry.getValue().size() > busiestOwners.size()) {
				busiest = entry.getKey();
				busiestOwners = entry.getValue();
			}
		}

		List<Finding> findings = new ArrayList<>();
		if (busiest != null && busiestOwners.size() >= 3) {
			String plain = busiest.substring(busiest.lastIndexOf('/') + 1);
			findings.add(new Finding(Severity.INFO, plain + ": " + busiestOwners.size() + " mods dentro",
				String.join(", ", busiestOwners) + " inyectan todos en esta clase. No es un conflicto "
					+ "por si mismo, pero es donde mas probable es que uno pise a otro.",
				"Mira la pestana Conflictos para ver si alguno choca de verdad.", ""));
		}

		long locked = KnobRegistry.locked().size();
		if (locked > 0) {
			findings.add(new Finding(Severity.INFO, locked + " configs no se pueden ajustar",
				"Estos mods no publican un IMixinConfigPlugin, el enganche que permite apagar partes "
					+ "sueltas. Se ven en el Taller, pero solo se pueden quitar enteros.",
				"Pestana Taller de mixins.", ""));
		}
		return findings;
	}

	/**
	 * Mixins whose target method is not in the class any more.
	 *
	 * <p>Grouped per mod: one mod with forty dead injections is one problem, not forty. Selectors
	 * without a resolvable plain name are skipped rather than guessed at.
	 */
	private static List<Finding> brokenMixins(List<MixinTarget> targets, List<InstalledMod> mods) {
		Map<String, String> names = new LinkedHashMap<>();
		for (InstalledMod mod : mods) {
			names.put(mod.id(), mod.displayName());
		}

		Map<String, List<MixinTarget>> deadByMod = new LinkedHashMap<>();
		Map<String, Integer> totalByMod = new LinkedHashMap<>();

		for (MixinTarget target : targets) {
			String plain = plainName(target.targetMethod());
			// Wildcards and regex selectors cannot be checked this way; not knowing is not a fault.
			if (plain.isBlank() || plain.contains("*") || plain.startsWith("/")) {
				continue;
			}
			totalByMod.merge(target.modId(), 1, Integer::sum);

			if (!TargetInspector.inspect(target.targetClass(), plain).exists()) {
				deadByMod.computeIfAbsent(target.modId(), ignored -> new ArrayList<>()).add(target);
			}
		}

		List<Finding> findings = new ArrayList<>();
		for (Map.Entry<String, List<MixinTarget>> entry : deadByMod.entrySet()) {
			String modId = entry.getKey();
			int dead = entry.getValue().size();
			int total = totalByMod.getOrDefault(modId, dead);
			String name = names.getOrDefault(modId, modId);

			// All of them missing means the mod is built for another version entirely.
			boolean whollyBroken = dead == total && total > 1;
			MixinTarget sample = entry.getValue().get(0);

			findings.add(new Finding(
				whollyBroken ? Severity.CRITICAL : Severity.WARNING,
				name + ": " + dead + (dead == 1 ? " inyeccion no aplica" : " inyecciones no aplican"),
				"Sus mixins apuntan a metodos que no existen en esta version de Minecraft, por ejemplo "
					+ sample.prettyTarget() + ". El mod carga sin quejarse, pero esa parte no hace nada."
					+ (whollyBroken ? " Ninguna de sus inyecciones encaja: parece compilado para otra version." : ""),
				modId,
				whollyBroken
					? "Busca una version de " + name + " para " + ModrinthClient.gameVersion() + "."
					: "Suele ser inofensivo si el mod tiene rutas alternativas, pero revisa si te falta alguna funcion."
			));
		}
		return findings;
	}

	/** How much heap the game got, and whether that number makes sense for this machine. */
	private static List<Finding> memoryAdvice() {
		List<Finding> findings = new ArrayList<>();
		HardwareProfile hardware = HardwareScanner.profile();

		long heap = hardware.allocatedRamMb();
		long total = hardware.totalRamMb();
		if (heap <= 0 || total <= 0) {
			return findings;
		}

		if (heap < LOW_HEAP_MB) {
			findings.add(new Finding(Severity.WARNING,
				"Poca memoria asignada al juego",
				"Minecraft tiene " + heap + " MB. Con mods por debajo de " + LOW_HEAP_MB
					+ " MB el recolector de basura entra a menudo y provoca tirones.",
				"", "Sube la memoria en tu launcher a unos 4096 MB."));
		} else if (heap > total * GREEDY_HEAP_SHARE) {
			findings.add(new Finding(Severity.WARNING,
				"Demasiada memoria asignada",
				"Minecraft tiene " + heap + " MB de los " + total + " MB del sistema. Dejar tan poco al "
					+ "sistema operativo y al driver grafico suele empeorar el rendimiento, no mejorarlo.",
				"", "Con 4096-8192 MB va sobrado para un cliente con mods."));
		} else {
			findings.add(new Finding(Severity.GOOD,
				"Memoria bien dimensionada",
				heap + " MB asignados de " + total + " MB. Es un reparto sano.", "", ""));
		}
		return findings;
	}

	/** Mods the catalog knows to be redundant with something else already installed. */
	private static List<Finding> redundantMods(List<InstalledMod> userMods) {
		List<Finding> findings = new ArrayList<>();

		for (InstalledMod mod : userMods) {
			CatalogEntry entry = Catalog.findInstalled(mod.id());
			if (entry == null) {
				continue;
			}
			for (String replacedId : entry.replaces()) {
				boolean stillThere = userMods.stream().anyMatch(other -> {
					CatalogEntry replaced = Catalog.find(replacedId);
					return replaced != null && replaced.matches(other.id());
				});
				if (stillThere) {
					findings.add(new Finding(Severity.INFO,
						"Redundancia: " + entry.name() + " y " + replacedId,
						entry.name() + " ya hace lo que hace " + replacedId + ". Tenerlos a la vez no aporta "
							+ "y duplica trabajo en cada frame.",
						mod.id(), "Puedes quitar " + replacedId + "."));
				}
			}
		}
		return findings;
	}

	/** Mods with a newer build published for this Minecraft version. */
	private static List<Finding> outdatedMods(List<InstalledMod> userMods) {
		List<CompletableFuture<Finding>> pending = new ArrayList<>();

		for (InstalledMod mod : userMods) {
			CatalogEntry entry = Catalog.findInstalled(mod.id());
			if (entry == null) {
				// Without a slug there is nothing to compare against, so it is simply not checked.
				continue;
			}
			pending.add(ModrinthClient.availability(entry.slug()).thenApply(availability -> {
				if (!availability.isInstallable()) {
					return null;
				}
				String latest = availability.version().versionNumber();
				if (looksSameVersion(mod.version(), latest)) {
					return null;
				}
				return new Finding(Severity.INFO,
					entry.name() + ": hay una version mas nueva",
					"Tienes " + mod.version() + " y la ultima para " + ModrinthClient.gameVersion()
						+ " es " + latest + " (" + availability.version().channel().label() + ").",
					mod.id(), "Actualizalo desde la pestana Mods.");
			}).exceptionally(throwable -> null));
		}

		List<Finding> findings = new ArrayList<>();
		for (CompletableFuture<Finding> future : pending) {
			try {
				Finding finding = future.join();
				if (finding != null) {
					findings.add(finding);
				}
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Update check failed for one mod", exception);
			}
		}
		return findings;
	}

	/**
	 * True when two version strings name the same build.
	 *
	 * <p>The previous test asked whether one string contained the other, and that quietly answers
	 * "same build" for 1.1.1 against 1.1.10 — the update existed, Mod Menu offered it, and this check
	 * reported everything fine. Comparing segment by segment as numbers is the only reading that puts
	 * 9 and 10 the right way round.
	 */
	private static boolean looksSameVersion(String installed, String latest) {
		int[] a = numericParts(installed);
		int[] b = numericParts(latest);
		if (a.length == 0 || b.length == 0) {
			// Nothing numeric to compare. Better to miss an update than to invent one.
			return true;
		}
		for (int index = 0; index < Math.max(a.length, b.length); index++) {
			int left = index < a.length ? a[index] : 0;
			int right = index < b.length ? b[index] : 0;
			if (left != right) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The numbers in a version string, in order.
	 *
	 * <p>Build metadata after {@code +} is dropped: {@code 0.9.1+mc26.1.2} and {@code 0.9.1} are the
	 * same release, and keeping the Minecraft version in the comparison would make every mod look
	 * different from itself. Everything else contributes, so {@code 0.4.4-beta2} and
	 * {@code 0.4.4-beta3} still compare as the different builds they are.
	 */
	private static int[] numericParts(String value) {
		if (value == null) {
			return new int[0];
		}
		String core = value;
		int plus = core.indexOf('+');
		if (plus > 0) {
			core = core.substring(0, plus);
		}

		List<Integer> parts = new ArrayList<>();
		int index = 0;
		while (index < core.length() && parts.size() < 8) {
			if (!Character.isDigit(core.charAt(index))) {
				index++;
				continue;
			}
			int end = index;
			while (end < core.length() && Character.isDigit(core.charAt(end))) {
				end++;
			}
			try {
				parts.add(Integer.parseInt(core.substring(index, Math.min(end, index + 9))));
			} catch (NumberFormatException ignored) {
				// A run of digits too long to be a version component: skip it.
			}
			index = end;
		}

		int[] out = new int[parts.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = parts.get(i);
		}
		return out;
	}

	private static String plainName(String selector) {
		String value = selector;
		int paren = value.indexOf('(');
		if (paren > 0) {
			value = value.substring(0, paren);
		}
		int semicolon = value.lastIndexOf(';');
		if (semicolon >= 0 && semicolon + 1 < value.length()) {
			value = value.substring(semicolon + 1);
		}
		return value.strip();
	}
}

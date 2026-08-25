package dev.zymekoh.optimatch.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups the raw injections by target method and decides which overlaps actually matter.
 *
 * <p>The rules follow how Mixin resolves collisions: an {@code @Overwrite} claims the whole method,
 * a {@code @Redirect} claims one call site and refuses to share it, while {@code @Inject} and the
 * MixinExtras wrappers are built to stack.
 */
public final class ConflictAnalyzer {
	private ConflictAnalyzer() {
	}

	public static List<Conflict> analyze(List<MixinTarget> targets) {
		return analyze(targets, java.util.Map.of());
	}

	/**
	 * @param displayNames mod id to human name, so contenders read as "Sodium" rather than "sodium"
	 */
	public static List<Conflict> analyze(List<MixinTarget> targets, java.util.Map<String, String> displayNames) {
		Map<String, List<MixinTarget>> grouped = new LinkedHashMap<>();
		for (MixinTarget target : targets) {
			// Grouping by the method SIGNATURE, not just its name: two mods on different overloads of
			// render(...) never meet, and collapsing them would invent a conflict.
			String key = target.targetClass() + "#" + signatureOf(target.targetMethod());
			grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(target);
		}

		List<Conflict> conflicts = new ArrayList<>();
		for (Map.Entry<String, List<MixinTarget>> entry : grouped.entrySet()) {
			Conflict conflict = evaluate(entry.getValue(), displayNames);
			if (conflict != null) {
				conflicts.add(conflict);
			}
		}

		// Worst first, then the busiest targets.
		conflicts.sort(Comparator
			.comparingInt((Conflict conflict) -> conflict.level().ordinal())
			.thenComparing(conflict -> -conflict.modIds().size()));
		return List.copyOf(conflicts);
	}

	private static Conflict evaluate(List<MixinTarget> group, java.util.Map<String, String> displayNames) {
		Set<String> modIds = new LinkedHashSet<>();
		for (MixinTarget target : group) {
			modIds.add(target.modId());
		}
		// A single mod stacking injections on its own method is its own business.
		if (modIds.size() < 2) {
			return null;
		}

		List<MixinTarget> overwrites = withSeverity(group, MixinTarget.Severity.EXCLUSIVE, MixinTarget.Kind.OVERWRITE);
		List<MixinTarget> exclusives = clashingAtSameSite(withSeverity(group, MixinTarget.Severity.EXCLUSIVE, null));
		List<MixinTarget> cooperatives = clashingAtSameSite(withSeverity(group, MixinTarget.Severity.COOPERATIVE, null));

		Conflict.Level level;
		String explanation;
		String advice;

		if (distinctMods(overwrites) >= 2) {
			level = Conflict.Level.CRITICAL;
			explanation = "Dos mods reescriben el metodo entero con @Overwrite. Solo puede ganar uno.";
			advice = "Quita uno de los dos: sus cambios son incompatibles por diseno.";
		} else if (!overwrites.isEmpty() && group.size() > overwrites.size()) {
			level = Conflict.Level.CRITICAL;
			String owner = overwrites.get(0).modId();
			explanation = owner + " reescribe el metodo con @Overwrite, asi que las inyecciones de los otros mods desaparecen.";
			advice = "Comprueba si el otro mod tiene una version compatible con " + owner + ".";
		} else if (distinctMods(exclusives) >= 2) {
			boolean samePriority = samePriority(exclusives);
			boolean siteKnown = exclusives.stream().allMatch(MixinTarget::hasKnownSite);

			if (!siteKnown) {
				// Honest about the limit: without a readable @At we know they share a method, not
				// that they share an instruction. Flagging it as critical would overstate the case.
				level = Conflict.Level.WARNING;
				explanation = "Varios mods usan inyecciones exclusivas en este metodo. No se pudo leer el punto "
					+ "exacto de al menos una, asi que no se puede confirmar si compiten por la misma instruccion.";
				advice = "Puede que convivan sin problema. Revisalo solo si notas algo raro.";
			} else {
				level = samePriority ? Conflict.Level.CRITICAL : Conflict.Level.WARNING;
				explanation = "Varios mods reclaman la MISMA instruccion con inyecciones exclusivas"
					+ (samePriority ? ", y con la misma prioridad, asi que el ganador es impredecible." : ".");
				advice = samePriority
					? "Es el caso mas problematico: uno de los dos no se aplicara. Prueba a quitar uno."
					: "Gana el de mayor prioridad. Suele funcionar, pero revisa si notas comportamiento raro.";
			}
		} else if (distinctMods(cooperatives) >= 2) {
			level = Conflict.Level.WARNING;
			explanation = "Varios mods envuelven la misma llamada. Se encadenan, pero el resultado depende del orden de carga.";
			advice = "No suele romper nada. Vigilalo solo si ves un comportamiento extrano.";
		} else if (hasOnlySeparateSites(group)) {
			// Same method, different instructions: they never see each other. Not worth reporting.
			return null;
		} else {
			level = Conflict.Level.SAFE;
			explanation = "Varios mods anaden codigo con @Inject en el mismo metodo. Es lo normal y conviven bien.";
			advice = "No hay que hacer nada.";
		}

		// One contender per injection, keeping the priority so a winner can be predicted.
		List<Conflict.Contender> contenders = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (MixinTarget target : group) {
			String key = target.modId() + "|" + target.kind() + "|" + target.mixinClass();
			if (seen.add(key)) {
				contenders.add(new Conflict.Contender(
					target.modId(),
					displayNames.getOrDefault(target.modId(), target.modId()),
					target.kind(),
					target.priority(),
					target.mixinClass()
				));
			}
		}

		MixinTarget first = group.get(0);
		return new Conflict(
			first.prettyTarget(),
			first.targetClass(),
			normalize(first.targetMethod()),
			List.copyOf(modIds),
			List.copyOf(contenders),
			level,
			explanation,
			advice
		);
	}

	/**
	 * Keeps only the injections that genuinely compete: those landing on the same instruction.
	 *
	 * <p>An {@code @Redirect} claims one call site, not the whole method. Two of them inside
	 * {@code <init>} that redirect different calls coexist without either noticing. Reporting those
	 * as a conflict is the difference between a tool worth reading and one that cries wolf.
	 *
	 * <p>An {@code @Overwrite} has no site — it takes the whole method — so it is never filtered here.
	 * Injections whose site could not be read are kept too: unknown is not the same as harmless.
	 */
	private static List<MixinTarget> clashingAtSameSite(List<MixinTarget> candidates) {
		if (candidates.size() < 2) {
			return candidates;
		}

		Map<String, List<MixinTarget>> bySite = new LinkedHashMap<>();
		for (MixinTarget target : candidates) {
			// Unreadable sites all share one bucket on purpose. Giving each its own would mean
			// "we could not tell" silently became "they are fine", which hides real collisions —
			// notably @ModifyConstant, which selects by constant value and carries no @At at all.
			String site = target.hasKnownSite() ? target.site() : "sitio-desconocido";
			bySite.computeIfAbsent(site, ignored -> new ArrayList<>()).add(target);
		}

		List<MixinTarget> clashing = new ArrayList<>();
		for (List<MixinTarget> sameSite : bySite.values()) {
			if (distinctMods(sameSite) >= 2) {
				clashing.addAll(sameSite);
			}
		}
		return clashing;
	}

	private static List<MixinTarget> withSeverity(List<MixinTarget> group, MixinTarget.Severity severity, MixinTarget.Kind exactKind) {
		List<MixinTarget> matches = new ArrayList<>();
		for (MixinTarget target : group) {
			boolean severityMatches = target.kind().severity() == severity;
			boolean kindMatches = exactKind == null || target.kind() == exactKind;
			if (severityMatches && kindMatches) {
				matches.add(target);
			}
		}
		return matches;
	}

	/**
	 * True when every exclusive injection in the group sits on its own instruction, so nothing is
	 * actually being fought over.
	 */
	private static boolean hasOnlySeparateSites(List<MixinTarget> group) {
		List<MixinTarget> exclusive = withSeverity(group, MixinTarget.Severity.EXCLUSIVE, null);
		if (exclusive.size() < 2) {
			return false;
		}
		return clashingAtSameSite(exclusive).isEmpty();
	}

	private static int distinctMods(List<MixinTarget> targets) {
		Set<String> mods = new LinkedHashSet<>();
		for (MixinTarget target : targets) {
			mods.add(target.modId());
		}
		return mods.size();
	}

	private static boolean samePriority(List<MixinTarget> targets) {
		if (targets.isEmpty()) {
			return false;
		}
		int priority = targets.get(0).priority();
		for (MixinTarget target : targets) {
			if (target.priority() != priority) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The grouping key for a selector: method name plus descriptor when one is present.
	 *
	 * <p>Keeping the descriptor is what stops two mods on different overloads of the same name from
	 * being merged into a phantom conflict. Selectors without a descriptor fall back to the name, and
	 * group with everything of that name — the safe direction, since we cannot tell them apart.
	 */
	private static String signatureOf(String selector) {
		String name = normalize(selector);
		int descriptorStart = selector.indexOf('(');
		if (descriptorStart < 0) {
			return name;
		}
		return name + selector.substring(descriptorStart);
	}

	/**
	 * Mixin selectors come in many shapes: a bare name, {@code name(desc)ret}, or a fully qualified
	 * {@code Lowner;name(desc)ret}. Reduce them all to the plain method name for display.
	 */
	private static String normalize(String selector) {
		String value = selector;
		int descriptor = value.indexOf('(');
		if (descriptor > 0) {
			value = value.substring(0, descriptor);
		}
		int semicolon = value.lastIndexOf(';');
		if (semicolon >= 0 && semicolon + 1 < value.length()) {
			value = value.substring(semicolon + 1);
		}
		int dot = value.lastIndexOf('.');
		if (dot >= 0 && dot + 1 < value.length()) {
			value = value.substring(dot + 1);
		}
		return value.strip();
	}
}

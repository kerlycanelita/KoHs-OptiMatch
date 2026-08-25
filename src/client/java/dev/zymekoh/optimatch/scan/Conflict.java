package dev.zymekoh.optimatch.scan;

import java.util.Comparator;
import java.util.List;

/**
 * Two or more mods reaching for the same Minecraft method.
 *
 * @param contenders one per injection, carrying enough to explain and to pick a winner
 */
public record Conflict(
	String targetLabel,
	String targetClass,
	String targetMethod,
	List<String> modIds,
	List<Contender> contenders,
	Level level,
	String explanation,
	String advice
) {
	/**
	 * One mod's claim on the target.
	 *
	 * @param priority mixin priority; when two exclusive injections meet, the higher number wins
	 */
	public record Contender(String modId, String displayName, MixinTarget.Kind kind, int priority,
							String mixinClass) {
	}

	/** How much the player should care. */
	public enum Level {
		/** One mod will silently lose. Expect broken behaviour. */
		CRITICAL("Preocupante"),
		/** They can coexist, but the outcome depends on load order. */
		WARNING("Vigilar"),
		/** Several mods touch the same method additively; this is normal and fine. */
		SAFE("Sin problema");

		private final String label;

		Level(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	/** True when exactly two mods are fighting — the case worth showing as a head-to-head. */
	public boolean isDuel() {
		return this.modIds.size() == 2 && this.level != Level.SAFE;
	}

	/**
	 * Who Mixin will actually pick, by priority. Returns null when it is a genuine tie, which is the
	 * worst case: the outcome then depends on load order and is not reproducible.
	 */
	public Contender predictedWinner() {
		List<Contender> exclusive = this.contenders.stream()
			.filter(contender -> contender.kind().severity() == MixinTarget.Severity.EXCLUSIVE)
			.sorted(Comparator.comparingInt(Contender::priority).reversed())
			.toList();

		if (exclusive.size() < 2) {
			return exclusive.isEmpty() ? null : exclusive.get(0);
		}
		return exclusive.get(0).priority() > exclusive.get(1).priority() ? exclusive.get(0) : null;
	}

	/** True when two exclusive injections share a priority, so the winner is unpredictable. */
	public boolean isTie() {
		return this.contenders.stream()
			.filter(contender -> contender.kind().severity() == MixinTarget.Severity.EXCLUSIVE)
			.count() >= 2 && predictedWinner() == null;
	}

	/** Plain method name, without descriptor, for looking the target up in the game's classes. */
	public String plainMethodName() {
		int paren = this.targetMethod.indexOf('(');
		return paren > 0 ? this.targetMethod.substring(0, paren) : this.targetMethod;
	}
}

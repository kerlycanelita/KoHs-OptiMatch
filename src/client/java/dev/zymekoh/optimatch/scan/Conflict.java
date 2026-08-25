package dev.zymekoh.optimatch.scan;

import java.util.List;

/**
 * Two or more mods reaching for the same Minecraft method.
 *
 * @param participants one line per mod involved, already formatted for display
 */
public record Conflict(
	String targetLabel,
	String targetClass,
	String targetMethod,
	List<String> modIds,
	List<String> participants,
	Level level,
	String explanation,
	String advice
) {
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
}

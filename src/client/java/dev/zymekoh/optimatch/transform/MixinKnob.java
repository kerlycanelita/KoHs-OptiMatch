package dev.zymekoh.optimatch.transform;

import java.util.List;

/**
 * One switch a player can actually move.
 *
 * <p>A knob is not a mixin: it is the lever the owning mod publishes for a group of its mixins.
 * Sodium exposes package rules in a properties file, ImmediatelyFast exposes booleans in its JSON.
 * Both end up here so the tab can present one uniform control.
 */
public record MixinKnob(String modId, String id, String label, String description, Kind kind,
						boolean defaultOn, Risk risk, List<String> mixinClasses, List<String> targets) {
	public enum Kind {
		/** A package rule written to a {@code *-mixins.properties} file. */
		RULE,
		/** A boolean field written to the mod's own JSON config. */
		FLAG
	}

	/** How much damage moving this knob can do, which decides whether a review is forced. */
	public enum Risk {
		/** Cosmetic or self-contained: turn it off and only that feature changes. */
		SAFE,
		/** Other mods inject into the same classes, so the outcome depends on load order. */
		SHARED,
		/** Core machinery the mod needs to function at all. */
		CORE
	}

	public String key() {
		return this.modId + ":" + this.id;
	}

	public int mixinCount() {
		return this.mixinClasses.size();
	}
}

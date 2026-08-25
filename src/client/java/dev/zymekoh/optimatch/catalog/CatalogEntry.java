package dev.zymekoh.optimatch.catalog;

import java.util.List;

/**
 * Curated knowledge about one mod. This is the part the Modrinth API cannot answer: whether a mod
 * helps frame rate or input latency, what it replaces, and which mods it is known to fight with.
 *
 * <p>Existence, current version and 26.1.2 compatibility are <em>not</em> stored here — those are
 * resolved live against Modrinth, so a stale entry simply disappears instead of misleading anyone.
 *
 * @param modId     the Fabric mod id, used to match against what is already installed
 * @param slug      the Modrinth project slug, used to resolve downloads
 * @param roles     what the mod does, most important first
 * @param fpsImpact rough frame-rate effect, -3 (heavy cost) to +3 (huge gain)
 * @param latencyImpact rough input-latency effect, -3 (adds delay) to +3 (removes a lot)
 * @param replaces  mod ids this one supersedes; running both is redundant
 * @param clashes     mod ids known to conflict at runtime
 * @param competitive whether a strong anticheat tolerates it
 * @param competitiveNote why it carries that rating, shown to the player
 */
public record CatalogEntry(
	String modId,
	String slug,
	String name,
	List<ModRole> roles,
	String summary,
	int fpsImpact,
	int latencyImpact,
	List<String> requires,
	List<String> replaces,
	List<String> clashes,
	boolean desktopOnly,
	Competitive competitive,
	String competitiveNote
) {
	/**
	 * How a mod stands with the anticheats used on competitive servers.
	 *
	 * <p>Based on the published rules of the big networks: performance and cosmetic-only mods are
	 * fine, while anything that automates an action, reveals hidden information, or changes the
	 * camera is not. Hypixel names minimaps and freelook/perspective mods as disallowed outright.
	 *
	 * <p>This is guidance, not a guarantee — every server writes its own rules and the final
	 * responsibility is the player's.
	 */
	public enum Competitive {
		/** Nothing an anticheat objects to: performance, input fidelity, or your own information. */
		ALLOWED,
		/** Tolerated somewhere, banned elsewhere. Kept out of the competitive preset. */
		RISKY,
		/** Explicitly disallowed by the major networks. Never suggested for competitive play. */
		BANNED
	}
	public boolean isCompetitiveSafe() {
		return this.competitive == Competitive.ALLOWED;
	}

	public ModRole primaryRole() {
		return this.roles.isEmpty() ? ModRole.LIBRARY : this.roles.get(0);
	}

	public boolean hasRole(ModRole role) {
		return this.roles.contains(role);
	}

	/** True when the mod actively hurts responsiveness, e.g. GUI frame-rate limiters. */
	public boolean hurtsLatency() {
		return this.latencyImpact < 0;
	}

	/**
	 * Whether an installed mod is this catalog entry.
	 *
	 * <p>A project's Modrinth slug and its Fabric mod id often disagree — {@code ferrite-core} ships
	 * as {@code ferritecore}, {@code cull-leaves} as {@code cullleaves}. Comparing both, with
	 * separators stripped, avoids recommending something the player already has.
	 */
	public boolean matches(String installedModId) {
		if (installedModId == null || installedModId.isBlank()) {
			return false;
		}
		String candidate = normalize(installedModId);
		return candidate.equals(normalize(this.modId)) || candidate.equals(normalize(this.slug));
	}

	private static String normalize(String value) {
		return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}

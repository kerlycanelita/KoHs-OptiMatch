package dev.zymekoh.optimatch.profile;

import java.util.List;
import java.util.Map;

/**
 * A named snapshot of a mod setup: which mods were active, how to get them back, and the contents of
 * their config files at the moment it was saved.
 *
 * <p>Storing the configs inline is what makes a profile a real restore point rather than a shopping
 * list — coming back to "PvP" brings your settings with it, not just the jars.
 *
 * @param entries one per mod, with the Modrinth slug needed to reinstall it
 * @param configs config path (relative to {@code config/}) to file contents
 * @param savedAt epoch millis, so the list can be shown newest first
 */
public record ModProfile(String name, List<Entry> entries, Map<String, String> configs, long savedAt) {
	/**
	 * One mod inside a profile.
	 *
	 * @param slug Modrinth slug, empty when the mod was not resolvable — it can still be listed,
	 *             just not reinstalled automatically
	 */
	public record Entry(String modId, String displayName, String version, String slug, String fileName) {
		public boolean isReinstallable() {
			return this.slug != null && !this.slug.isBlank();
		}
	}

	public int size() {
		return this.entries.size();
	}

	public boolean contains(String modId) {
		return this.entries.stream().anyMatch(entry -> entry.modId().equals(modId));
	}

	public List<String> modIds() {
		return this.entries.stream().map(Entry::modId).toList();
	}

	public int configCount() {
		return this.configs.size();
	}
}

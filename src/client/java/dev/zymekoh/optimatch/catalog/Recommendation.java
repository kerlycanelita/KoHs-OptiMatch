package dev.zymekoh.optimatch.catalog;

import java.util.List;

/**
 * The result of applying a {@link Preset} to a specific machine and mod folder.
 *
 * @param install     mods the player does not have yet, most relevant first
 * @param alreadyHave entries already installed, so the player sees the preset is partly met
 * @param warnings    installed mods that work against the chosen goal, with the reason
 */
public record Recommendation(
	Preset preset,
	List<Suggestion> install,
	List<Suggestion> alreadyHave,
	List<Warning> warnings
) {
	/**
	 * One recommended mod together with <em>why this machine in particular</em> wants it.
	 *
	 * @param relevance 0-100; drives the ordering so the biggest win for this hardware comes first
	 * @param reason    the hardware fact that earned it a place, e.g. "8 GB de RAM: ..."
	 */
	public record Suggestion(CatalogEntry entry, int relevance, String reason) {
		public String modId() {
			return this.entry.modId();
		}
	}

	/** An installed mod that undermines the selected preset. */
	public record Warning(String modId, String name, String reason) {
	}

	public boolean isEmpty() {
		return this.install.isEmpty();
	}

	public int totalConsidered() {
		return this.install.size() + this.alreadyHave.size();
	}

	/** Catalog entries only, for the bulk Modrinth availability lookup. */
	public List<CatalogEntry> installEntries() {
		return this.install.stream().map(Suggestion::entry).toList();
	}
}

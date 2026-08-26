package dev.zymekoh.optimatch.catalog;

import java.util.List;

/**
 * A concrete downloadable build that Modrinth confirms targets the running Minecraft version and the
 * Fabric loader. Nothing is ever offered to the player without one of these behind it.
 *
 * @param sha512      hash of the primary file, verified after download
 * @param datePublished ISO-8601 timestamp from Modrinth; the only reliable way to order builds
 * @param requiredDependencies Modrinth project ids that must also be installed
 */
public record ModrinthVersion(
	String projectId,
	String versionNumber,
	Channel channel,
	String fileName,
	String downloadUrl,
	String sha512,
	long fileSize,
	String gameVersion,
	String datePublished,
	List<String> requiredDependencies
) {
	/**
	 * Release channel. For a freshly released Minecraft, most mods only have alpha or beta builds —
	 * refusing anything but {@link #RELEASE} would leave the player with nothing, so the channel is
	 * surfaced in the UI instead of used as a filter.
	 */
	public enum Channel {
		RELEASE("estable", 0),
		BETA("beta", 1),
		ALPHA("alpha", 2);

		private final String label;
		private final int rank;

		Channel(String label, int rank) {
			this.label = label;
			this.rank = rank;
		}

		public String label() {
			return this.label;
		}

		/** Lower is more trustworthy; used to prefer a release over a beta over an alpha. */
		public int rank() {
			return this.rank;
		}

		public static Channel parse(String raw) {
			if (raw == null) {
				return ALPHA;
			}
			return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
				case "release" -> RELEASE;
				case "beta" -> BETA;
				default -> ALPHA;
			};
		}
	}

	public boolean isStable() {
		return this.channel == Channel.RELEASE;
	}

	public String sizeLabel() {
		return humanSize(this.fileSize);
	}

	/** Most mods are well under a megabyte, so a MB-only label would read as "0,0 MB". */
	public static String humanSize(long bytes) {
		if (bytes <= 0) {
			return "";
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		if (bytes < 1024 * 1024) {
			return String.format("%.0f KB", bytes / 1024.0);
		}
		return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
	}
}

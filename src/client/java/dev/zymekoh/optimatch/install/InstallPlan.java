package dev.zymekoh.optimatch.install;

import dev.zymekoh.optimatch.catalog.ModrinthVersion;
import java.util.List;

/**
 * Exactly what an install would download, resolved before a single byte is fetched so the player can
 * see the full list — the mod plus every required dependency — and approve it.
 *
 * @param files    every jar to download, the requested mod first
 * @param blockers reasons the install cannot proceed; when non-empty nothing is downloaded at all
 */
public record InstallPlan(
	String requestedSlug,
	String displayName,
	List<ModrinthVersion> files,
	List<String> blockers
) {
	public boolean isBlocked() {
		return !this.blockers.isEmpty();
	}

	public long totalBytes() {
		long total = 0;
		for (ModrinthVersion file : this.files) {
			total += file.fileSize();
		}
		return total;
	}

	public String totalSizeLabel() {
		long bytes = totalBytes();
		return bytes <= 0 ? "tamano desconocido" : ModrinthVersion.humanSize(bytes);
	}

	/** Dependencies are everything after the requested mod itself. */
	public int dependencyCount() {
		return Math.max(0, this.files.size() - 1);
	}
}

package dev.zymekoh.optimatch.catalog;

import java.util.List;

/**
 * A project as returned by Modrinth search or the project endpoint.
 *
 * @param iconUrl  taken from {@code raw_icon_url} (a PNG). The {@code icon_url} field is a WebP,
 *                 which {@code NativeImage.read} cannot decode.
 * @param body     full README markdown; empty for search hits, filled by the details lookup
 * @param links    author-provided links (source, issues, wiki, Discord, donations)
 */
public record ModrinthProject(
	String projectId,
	String slug,
	String title,
	String description,
	String iconUrl,
	List<String> categories,
	int downloads,
	String clientSide,
	String serverSide,
	String sourceUrl,
	String body,
	List<ProjectLink> links
) {
	public String pageUrl() {
		return "https://modrinth.com/mod/" + this.slug;
	}

	public String downloadsLabel() {
		if (this.downloads >= 1_000_000) {
			return String.format("%.1fM descargas", this.downloads / 1_000_000.0);
		}
		if (this.downloads >= 1_000) {
			return String.format("%.0fk descargas", this.downloads / 1_000.0);
		}
		return this.downloads + " descargas";
	}

	/** True when the mod does nothing on a client-only install. */
	public boolean isServerOnly() {
		return "unsupported".equals(this.clientSide);
	}
}

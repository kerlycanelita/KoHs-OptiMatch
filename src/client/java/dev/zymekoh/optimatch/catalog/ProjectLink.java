package dev.zymekoh.optimatch.catalog;

import dev.zymekoh.optimatch.ui.LinkIcons;
import java.net.URI;

/**
 * One author-provided link from a Modrinth project: source, issues, wiki, Discord or a donation
 * page.
 *
 * <p>These URLs are written by mod authors, so they are treated as untrusted input: only
 * {@code https} and {@code http} are ever offered, and the UI shows the real host before the player
 * clicks, rather than hiding an arbitrary destination behind a friendly label.
 */
public record ProjectLink(LinkIcons.Kind kind, String label, String url) {
	/** Builds a link, or null when the URL is missing or not something safe to open. */
	public static ProjectLink of(LinkIcons.Kind kind, String label, String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		try {
			URI parsed = URI.create(url.strip());
			String scheme = parsed.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
				return null;
			}
			if (parsed.getHost() == null || parsed.getHost().isBlank()) {
				return null;
			}
			return new ProjectLink(kind, label == null || label.isBlank() ? kind.label() : label, url.strip());
		} catch (Exception exception) {
			return null;
		}
	}

	/** The bare host, shown so the destination is never a surprise. */
	public String host() {
		try {
			String host = URI.create(this.url).getHost();
			return host == null ? this.url : host.startsWith("www.") ? host.substring(4) : host;
		} catch (Exception exception) {
			return this.url;
		}
	}

	public boolean isDonation() {
		return switch (this.kind) {
			case KOFI, BUY_ME_A_COFFEE, PATREON, PAYPAL, GITHUB_SPONSORS -> true;
			default -> false;
		};
	}
}

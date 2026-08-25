package dev.zymekoh.optimatch.config;

import java.nio.file.Path;
import java.util.Locale;

/**
 * A config file on disk that belongs to an installed mod.
 *
 * @param relativePath path shown to the player, relative to the game's {@code config/} directory
 */
public record EditableFile(Path path, String relativePath, String fileName, long sizeBytes) {
	/** File formats the editor knows how to colour. */
	public enum Format {
		JSON, TOML, PROPERTIES, YAML, PLAIN
	}

	public Format format() {
		String lower = this.fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".json") || lower.endsWith(".json5")) {
			return Format.JSON;
		}
		if (lower.endsWith(".toml")) {
			return Format.TOML;
		}
		if (lower.endsWith(".properties")) {
			return Format.PROPERTIES;
		}
		if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
			return Format.YAML;
		}
		return Format.PLAIN;
	}

	public String sizeLabel() {
		if (this.sizeBytes < 1024) {
			return this.sizeBytes + " B";
		}
		return String.format("%.1f KB", this.sizeBytes / 1024.0);
	}

	/** Extension without the dot, for the little type tag in the browser. */
	public String extension() {
		int dot = this.fileName.lastIndexOf('.');
		return dot > 0 && dot + 1 < this.fileName.length()
			? this.fileName.substring(dot + 1).toUpperCase(Locale.ROOT)
			: "—";
	}
}

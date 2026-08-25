package dev.zymekoh.optimatch.config;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.scan.InstalledMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Finds the config files a given installed mod wrote into {@code config/}.
 *
 * <p>There is no registry for this — mods just write where they like — so matching is by name.
 * Checked against a real instance, the common shapes all work out: {@code immediatelyfast.json},
 * {@code sodium-options.json} and {@code sodium-mixins.properties} all carry their mod id as a
 * prefix, and mods with several files tend to use a {@code config/<modid>/} folder.
 */
public final class ModConfigLocator {
	/** Deep enough for {@code config/<modid>/sub/file.json}, shallow enough to stay fast. */
	private static final int MAX_DEPTH = 4;

	/** Anything larger is not a config a human hand-edits, and would bog the editor down. */
	public static final long MAX_EDITABLE_BYTES = 512 * 1024;

	private static final List<String> EDITABLE_EXTENSIONS =
		List.of(".json", ".json5", ".toml", ".properties", ".yml", ".yaml", ".conf", ".cfg", ".txt", ".ini");

	private ModConfigLocator() {
	}

	public static Path configRoot() {
		return FabricLoader.getInstance().getConfigDir();
	}

	/** Every editable config file that appears to belong to {@code mod}, folders first then names. */
	public static List<EditableFile> filesFor(InstalledMod mod) {
		Path root = configRoot();
		if (!Files.isDirectory(root)) {
			return List.of();
		}

		String modKey = normalize(mod.id());
		String nameKey = normalize(mod.displayName());
		List<EditableFile> found = new ArrayList<>();

		try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
			walk.filter(Files::isRegularFile).forEach(path -> {
				String fileName = path.getFileName().toString();
				if (!isEditableExtension(fileName)) {
					return;
				}

				Path relative = root.relativize(path);
				// A file matches if its own name carries the mod id, or if any parent folder does —
				// that is what catches the config/<modid>/... layout.
				String haystack = normalize(relative.toString());
				boolean matches = !modKey.isEmpty() && haystack.contains(modKey)
					|| nameKey.length() >= 4 && haystack.contains(nameKey);
				if (!matches) {
					return;
				}

				try {
					long size = Files.size(path);
					found.add(new EditableFile(path, relative.toString().replace('\\', '/'), fileName, size));
				} catch (Exception ignored) {
					// A file that vanished mid-walk is simply not listed.
				}
			});
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Could not scan the config directory for {}", mod.id(), exception);
			return List.of();
		}

		found.sort(Comparator.comparing(EditableFile::relativePath, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(found);
	}

	/** True when the mod has anything at all to edit, used to enable the button. */
	public static boolean hasConfig(InstalledMod mod) {
		return !filesFor(mod).isEmpty();
	}

	private static boolean isEditableExtension(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		for (String extension : EDITABLE_EXTENSIONS) {
			if (lower.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	/** Strips separators so {@code sodium-options} still matches the id {@code sodium}. */
	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}

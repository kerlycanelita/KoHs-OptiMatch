package dev.zymekoh.optimatch.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.catalog.Catalog;
import dev.zymekoh.optimatch.catalog.CatalogEntry;
import dev.zymekoh.optimatch.config.EditableFile;
import dev.zymekoh.optimatch.config.ModConfigLocator;
import dev.zymekoh.optimatch.install.ModInstaller;
import dev.zymekoh.optimatch.scan.InstalledMod;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Reads and writes the saved profiles in {@code config/kohs_optimatch/profiles.json}. */
public final class ProfileStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Guard against a runaway config turning profiles.json into something enormous. */
	private static final long MAX_STORED_CONFIG_BYTES = 256 * 1024;

	private static List<ModProfile> cache;

	private ProfileStore() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir()
			.resolve(OptiMatchClient.MOD_ID)
			.resolve("profiles.json");
	}

	public static List<ModProfile> profiles() {
		if (cache == null) {
			cache = load();
		}
		return cache;
	}

	/**
	 * Captures the current setup: every user-facing mod, the slug needed to reinstall it, and the
	 * contents of every config file those mods own.
	 */
	public static ModProfile capture(String name, List<InstalledMod> installed) {
		List<ModProfile.Entry> entries = new ArrayList<>();
		Map<String, String> configs = new LinkedHashMap<>();

		for (InstalledMod mod : installed) {
			if (!mod.isUserFacing()) {
				continue;
			}

			CatalogEntry known = Catalog.findInstalled(mod.id());
			entries.add(new ModProfile.Entry(
				mod.id(),
				mod.displayName(),
				mod.version(),
				known != null ? known.slug() : "",
				mod.sourceFile()
			));

			for (EditableFile config : ModConfigLocator.filesFor(mod)) {
				if (config.sizeBytes() > MAX_STORED_CONFIG_BYTES) {
					continue;
				}
				try {
					configs.put(config.relativePath(), Files.readString(config.path(), StandardCharsets.UTF_8));
				} catch (Exception exception) {
					OptiMatchClient.LOGGER.debug("Could not snapshot config {}", config.relativePath(), exception);
				}
			}
		}

		return new ModProfile(name, List.copyOf(entries), Map.copyOf(configs), System.currentTimeMillis());
	}

	public static void save(ModProfile profile) {
		List<ModProfile> profiles = new ArrayList<>(profiles());
		// A profile name is its identity: saving over one replaces it.
		profiles.removeIf(existing -> existing.name().equalsIgnoreCase(profile.name()));
		profiles.add(profile);
		profiles.sort(Comparator.comparingLong(ModProfile::savedAt).reversed());
		cache = List.copyOf(profiles);
		persist();
	}

	/** Restores every config file the profile captured, overwriting what is there now. */
	public static int restoreConfigs(ModProfile profile) {
		int restored = 0;
		Path root = ModConfigLocator.configRoot();

		for (Map.Entry<String, String> saved : profile.configs().entrySet()) {
			try {
				Path target = root.resolve(saved.getKey());
				// Never let a crafted path escape the config directory.
				if (!target.normalize().startsWith(root.normalize())) {
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.writeString(target, saved.getValue(), StandardCharsets.UTF_8);
				restored++;
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.warn("Could not restore config {}", saved.getKey(), exception);
			}
		}
		return restored;
	}

	/**
	 * Deletes a profile and, when asked, the jars and config files it covers.
	 *
	 * <p>This is destructive and irreversible, so the caller must have confirmed it. Only mods listed
	 * in the profile are touched, and only files inside {@code mods/} and {@code config/}.
	 *
	 * @return a short human-readable summary of what was removed
	 */
	public static String delete(String name, boolean alsoRemoveModsAndConfigs) {
		ModProfile target = profiles().stream()
			.filter(profile -> profile.name().equalsIgnoreCase(name))
			.findFirst()
			.orElse(null);

		int removedJars = 0;
		int removedConfigs = 0;

		if (target != null && alsoRemoveModsAndConfigs) {
			Path mods = ModInstaller.modsDirectory();
			for (ModProfile.Entry entry : target.entries()) {
				if (entry.fileName() == null || entry.fileName().isBlank()) {
					continue;
				}
				try {
					Path jar = mods.resolve(entry.fileName());
					if (jar.normalize().startsWith(mods.normalize()) && Files.deleteIfExists(jar)) {
						removedJars++;
					}
				} catch (Exception exception) {
					// On Windows the loader holds the jar open; it can only go once the game exits.
					OptiMatchClient.LOGGER.warn("Could not delete {} (in use until restart)", entry.fileName());
				}
			}

			Path configRoot = ModConfigLocator.configRoot();
			for (String relative : target.configs().keySet()) {
				try {
					Path config = configRoot.resolve(relative);
					if (config.normalize().startsWith(configRoot.normalize()) && Files.deleteIfExists(config)) {
						removedConfigs++;
					}
				} catch (Exception exception) {
					OptiMatchClient.LOGGER.warn("Could not delete config {}", relative, exception);
				}
			}
		}

		List<ModProfile> profiles = new ArrayList<>(profiles());
		boolean removed = profiles.removeIf(existing -> existing.name().equalsIgnoreCase(name));
		if (removed) {
			cache = List.copyOf(profiles);
			persist();
		}

		if (!alsoRemoveModsAndConfigs) {
			return "Perfil eliminado.";
		}
		return "Perfil eliminado: " + removedJars + " jars y " + removedConfigs + " configs borrados.";
	}

	private static List<ModProfile> load() {
		Path path = file();
		if (!Files.exists(path)) {
			return List.of();
		}

		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				return List.of();
			}
			JsonArray array = root.getAsJsonObject().getAsJsonArray("profiles");
			if (array == null) {
				return List.of();
			}

			List<ModProfile> loaded = new ArrayList<>();
			for (JsonElement element : array) {
				JsonObject object = element.getAsJsonObject();

				List<ModProfile.Entry> entries = new ArrayList<>();
				JsonArray rawEntries = object.getAsJsonArray("entries");
				if (rawEntries != null) {
					for (JsonElement raw : rawEntries) {
						JsonObject entry = raw.getAsJsonObject();
						entries.add(new ModProfile.Entry(
							text(entry, "modId"),
							text(entry, "displayName"),
							text(entry, "version"),
							text(entry, "slug"),
							text(entry, "fileName")
						));
					}
				}

				Map<String, String> configs = new LinkedHashMap<>();
				JsonObject rawConfigs = object.getAsJsonObject("configs");
				if (rawConfigs != null) {
					for (String key : rawConfigs.keySet()) {
						configs.put(key, rawConfigs.get(key).getAsString());
					}
				}

				loaded.add(new ModProfile(
					text(object, "name"),
					List.copyOf(entries),
					Map.copyOf(configs),
					object.has("savedAt") ? object.get("savedAt").getAsLong() : 0L
				));
			}
			loaded.sort(Comparator.comparingLong(ModProfile::savedAt).reversed());
			return List.copyOf(loaded);
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.error("Could not read the saved profiles", exception);
			return List.of();
		}
	}

	private static String text(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static void persist() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());

			JsonArray array = new JsonArray();
			for (ModProfile profile : profiles()) {
				JsonObject object = new JsonObject();
				object.addProperty("name", profile.name());
				object.addProperty("savedAt", profile.savedAt());

				JsonArray entries = new JsonArray();
				for (ModProfile.Entry entry : profile.entries()) {
					JsonObject item = new JsonObject();
					item.addProperty("modId", entry.modId());
					item.addProperty("displayName", entry.displayName());
					item.addProperty("version", entry.version());
					item.addProperty("slug", entry.slug());
					item.addProperty("fileName", entry.fileName());
					entries.add(item);
				}
				object.add("entries", entries);

				JsonObject configs = new JsonObject();
				profile.configs().forEach(configs::addProperty);
				object.add("configs", configs);

				array.add(object);
			}

			JsonObject root = new JsonObject();
			root.add("profiles", array);

			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.error("Could not save the profiles", exception);
		}
	}
}

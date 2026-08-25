package dev.zymekoh.optimatch.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads and indexes the bundled curated catalog. */
public final class Catalog {
	private static final String RESOURCE = "/assets/kohs_optimatch/catalog/mods.json";

	private static Map<String, CatalogEntry> byModId;
	/** Where the entries in use came from, shown in the UI so the source is never a mystery. */
	private static String source = "incrustado";

	private Catalog() {
	}

	public static Map<String, CatalogEntry> entries() {
		if (byModId == null) {
			byModId = load();
		}
		return byModId;
	}

	public static String source() {
		return source;
	}

	/**
	 * Checks for a newer published catalog and swaps it in if one arrives.
	 *
	 * <p>Safe to call at startup: it runs off-thread and the bundled catalog stays in use until a
	 * download has been parsed successfully, so performance data can be corrected without shipping a
	 * new build of the mod.
	 */
	public static void refreshFromRemote() {
		RemoteCatalog.refresh().thenAccept(updated -> updated.ifPresent(json -> {
			Map<String, CatalogEntry> parsed = parseAll(json);
			if (!parsed.isEmpty()) {
				byModId = parsed;
				source = "remoto";
				OptiMatchClient.LOGGER.info("Catalog replaced from remote: {} entries", parsed.size());
			}
		}));
	}

	public static CatalogEntry find(String modId) {
		return entries().get(modId);
	}

	/**
	 * Looks up the entry for a mod that is actually installed, tolerating the common mismatch between
	 * a project's Modrinth slug and its Fabric mod id.
	 */
	public static CatalogEntry findInstalled(String installedModId) {
		CatalogEntry exact = entries().get(installedModId);
		if (exact != null) {
			return exact;
		}
		for (CatalogEntry entry : entries().values()) {
			if (entry.matches(installedModId)) {
				return entry;
			}
		}
		return null;
	}

	public static List<CatalogEntry> withRole(ModRole role) {
		List<CatalogEntry> matches = new ArrayList<>();
		for (CatalogEntry entry : entries().values()) {
			if (entry.hasRole(role)) {
				matches.add(entry);
			}
		}
		return matches;
	}

	/**
	 * Loads the best catalog available without touching the network: a previously downloaded one if
	 * it is still usable, otherwise the copy bundled in the jar.
	 */
	private static Map<String, CatalogEntry> load() {
		Optional<String> cached = RemoteCatalog.cached();
		if (cached.isPresent()) {
			Map<String, CatalogEntry> fromCache = parseAll(cached.get());
			if (!fromCache.isEmpty()) {
				source = "cache";
				OptiMatchClient.LOGGER.info("Loaded {} catalog entries from cache", fromCache.size());
				return fromCache;
			}
		}

		try (InputStream stream = Catalog.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				OptiMatchClient.LOGGER.error("Bundled catalog {} is missing from the jar", RESOURCE);
				return new LinkedHashMap<>();
			}
			String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			Map<String, CatalogEntry> bundled = parseAll(json);
			source = "incrustado";
			OptiMatchClient.LOGGER.info("Loaded {} curated catalog entries", bundled.size());
			return bundled;
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.error("Could not read the curated catalog", exception);
			return new LinkedHashMap<>();
		}
	}

	/** Parses a whole catalog document; returns empty when nothing usable came out of it. */
	private static Map<String, CatalogEntry> parseAll(String json) {
		Map<String, CatalogEntry> loaded = new LinkedHashMap<>();
		try {
			JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("entries");
			for (JsonElement element : array) {
				CatalogEntry entry = parse(element.getAsJsonObject());
				if (entry != null) {
					loaded.put(entry.modId(), entry);
				}
			}
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.warn("Could not parse a catalog document", exception);
			return new LinkedHashMap<>();
		}
		return loaded;
	}

	private static CatalogEntry parse(JsonObject object) {
		try {
			List<ModRole> roles = new ArrayList<>();
			JsonArray rawRoles = object.getAsJsonArray("roles");
			if (rawRoles != null) {
				for (JsonElement role : rawRoles) {
					try {
						roles.add(ModRole.valueOf(role.getAsString().toUpperCase(Locale.ROOT)));
					} catch (IllegalArgumentException ignored) {
						// An unknown role means the catalog is newer than this build; skip just that role.
					}
				}
			}

			return new CatalogEntry(
				object.get("modId").getAsString(),
				object.get("slug").getAsString(),
				object.get("name").getAsString(),
				List.copyOf(roles),
				string(object, "summary"),
				integer(object, "fpsImpact"),
				integer(object, "latencyImpact"),
				strings(object, "requires"),
				strings(object, "replaces"),
				strings(object, "clashes"),
				object.has("desktopOnly") && object.get("desktopOnly").getAsBoolean()
			);
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.warn("Skipping malformed catalog entry: {}", object, exception);
			return null;
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static int integer(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? 0 : element.getAsInt();
	}

	private static List<String> strings(JsonObject object, String key) {
		JsonArray array = object.getAsJsonArray(key);
		if (array == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			values.add(element.getAsString());
		}
		return List.copyOf(values);
	}
}

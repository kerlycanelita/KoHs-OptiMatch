package dev.zymekoh.optimatch.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Keeps the performance catalog current without shipping a new build of the mod.
 *
 * <p>The catalog is just a static JSON file, so it can live anywhere that serves files over HTTPS —
 * GitHub raw, GitHub Pages, or any CDN. No server code is needed. The client:
 *
 * <ol>
 *   <li>sends {@code If-None-Match} with the {@code ETag} it stored last time, so an unchanged file
 *       costs a {@code 304} and no body;</li>
 *   <li>writes what it gets to {@code config/kohs_optimatch/catalog-cache.json};</li>
 *   <li>falls back cache → bundled if the network is down or the payload is malformed.</li>
 * </ol>
 *
 * <p>The fetch is asynchronous and never blocks startup: the bundled catalog loads immediately and
 * the remote one replaces it when it arrives. A newer file is only accepted if it parses and has
 * entries, so a truncated download or a bad deploy cannot leave the player with an empty catalog.
 *
 * <p>{@code schemaVersion} lets the format change later without breaking older clients: anything
 * above {@link #SUPPORTED_SCHEMA} is ignored rather than misread.
 */
public final class RemoteCatalog {
	/** Where the published catalog lives. Overridable per-instance, see {@link #resolveUrl()}. */
	private static final String DEFAULT_URL =
		"https://raw.githubusercontent.com/kerlycanelita/KoHs-OptiMatch/main/catalog/mods.json";

	/** Highest catalog format this build understands. */
	public static final int SUPPORTED_SCHEMA = 1;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(6))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private RemoteCatalog() {
	}

	private static Path stateDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(OptiMatchClient.MOD_ID);
	}

	private static Path cacheFile() {
		return stateDir().resolve("catalog-cache.json");
	}

	private static Path etagFile() {
		return stateDir().resolve("catalog-cache.etag");
	}

	/** Lets an instance point at a different catalog without recompiling. */
	private static Path urlOverrideFile() {
		return stateDir().resolve("catalog-url.txt");
	}

	private static String resolveUrl() {
		try {
			Path override = urlOverrideFile();
			if (Files.exists(override)) {
				String url = Files.readString(override, StandardCharsets.UTF_8).strip();
				if (url.startsWith("https://")) {
					return url;
				}
			}
		} catch (Exception ignored) {
			// A broken override is not worth failing over; fall through to the default.
		}
		return DEFAULT_URL;
	}

	/** The newest catalog JSON available offline: the cached download, if it is usable. */
	public static Optional<String> cached() {
		try {
			Path cache = cacheFile();
			if (Files.exists(cache)) {
				String text = Files.readString(cache, StandardCharsets.UTF_8);
				if (isUsable(text)) {
					return Optional.of(text);
				}
			}
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Could not read the cached catalog", exception);
		}
		return Optional.empty();
	}

	/**
	 * Checks for a newer catalog in the background.
	 *
	 * @return the new JSON when one was downloaded and accepted, otherwise empty
	 */
	public static CompletableFuture<Optional<String>> refresh() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(resolveUrl()))
					.header("User-Agent", "KoHs-OptiMatch/0.1.0 (Minecraft Fabric mod selector)")
					.header("Accept", "application/json")
					.timeout(Duration.ofSeconds(10))
					.GET();

				// A stored ETag turns the usual check into a cheap 304.
				Path etagPath = etagFile();
				if (Files.exists(etagPath) && Files.exists(cacheFile())) {
					request.header("If-None-Match", Files.readString(etagPath, StandardCharsets.UTF_8).strip());
				}

				HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 304) {
					OptiMatchClient.LOGGER.debug("Catalog unchanged (304)");
					return Optional.<String>empty();
				}
				if (response.statusCode() != 200) {
					OptiMatchClient.LOGGER.debug("Catalog fetch returned {}", response.statusCode());
					return Optional.<String>empty();
				}

				String body = response.body();
				if (!isUsable(body)) {
					// A malformed or empty payload must never replace a working catalog.
					OptiMatchClient.LOGGER.warn("Downloaded catalog rejected: unusable payload");
					return Optional.<String>empty();
				}

				Files.createDirectories(stateDir());
				Files.writeString(cacheFile(), body, StandardCharsets.UTF_8);
				response.headers().firstValue("ETag").ifPresent(tag -> {
					try {
						Files.writeString(etagFile(), tag, StandardCharsets.UTF_8);
					} catch (Exception ignored) {
						// Losing the ETag only costs a full download next time.
					}
				});

				OptiMatchClient.LOGGER.info("Catalog updated from {}", resolveUrl());
				return Optional.of(body);
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Catalog refresh failed", exception);
				return Optional.<String>empty();
			}
		});
	}

	/** A payload is only accepted if it parses, targets a schema we know, and actually has entries. */
	private static boolean isUsable(String json) {
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				return false;
			}
			JsonObject root = parsed.getAsJsonObject();

			if (root.has("schemaVersion") && root.get("schemaVersion").getAsInt() > SUPPORTED_SCHEMA) {
				OptiMatchClient.LOGGER.warn("Catalog schema {} is newer than this build supports ({})",
					root.get("schemaVersion").getAsInt(), SUPPORTED_SCHEMA);
				return false;
			}

			return root.has("entries")
				&& root.get("entries").isJsonArray()
				&& !root.getAsJsonArray("entries").isEmpty();
		} catch (Exception exception) {
			return false;
		}
	}
}

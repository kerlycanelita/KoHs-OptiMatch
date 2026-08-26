package dev.zymekoh.optimatch.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.ui.LinkIcons;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Resolves catalog entries against Modrinth, and — critically — refuses to hand back anything that
 * is not built for the Minecraft version actually running.
 *
 * <p>Recommending a mod with no build for this version is the worst thing this tool could do: the
 * player ends up with a jar the loader rejects, or worse, one that loads and breaks. So the only
 * query used is the one Modrinth filters server-side:
 *
 * <pre>GET /v2/project/{slug}/version?loaders=["fabric"]&amp;game_versions=["26.1.2"]</pre>
 *
 * <p>An empty array is a definitive "no build for this version" and the entry is dropped. There is
 * deliberately no fallback to a nearby version.
 */
public final class ModrinthClient {
	private static final String API = "https://api.modrinth.com/v2";

	/** Modrinth blocks traffic that does not identify itself; see their API docs. */
	private static final String USER_AGENT = "KoHs-OptiMatch/0.1.0 (Minecraft Fabric mod selector)";

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(8))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/** One lookup per slug per session; Modrinth allows 300 requests a minute and we stay far below. */
	private static final Map<String, CompletableFuture<Availability>> CACHE = new ConcurrentHashMap<>();

	private static volatile String gameVersion;

	private ModrinthClient() {
	}

	/** What the live check concluded about one catalog entry. */
	public record Availability(Status status, ModrinthVersion version, String detail) {
		public enum Status {
			/** A build exists for this exact Minecraft version. */
			COMPATIBLE,
			/** The project exists but publishes nothing for this Minecraft version. */
			NO_BUILD_FOR_VERSION,
			/** The project id or slug does not resolve. */
			NOT_FOUND,
			/** Modrinth could not be reached; nothing may be offered on this basis. */
			OFFLINE
		}

		public boolean isInstallable() {
			return this.status == Status.COMPATIBLE && this.version != null;
		}
	}

	/**
	 * The Minecraft version the game is actually running, read from the loader rather than hardcoded,
	 * so the gate stays correct if the mod is used on a different build.
	 */
	public static String gameVersion() {
		if (gameVersion == null) {
			gameVersion = FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		}
		return gameVersion;
	}

	/** Resolves the best build of {@code slug} for this Minecraft version, or explains why there is none. */
	public static CompletableFuture<Availability> availability(String slug) {
		return CACHE.computeIfAbsent(slug, key -> CompletableFuture.supplyAsync(() -> lookup(key)));
	}

	/** Resolves many entries at once and keeps only the ones that are genuinely installable. */
	public static CompletableFuture<Map<String, Availability>> availabilityOf(List<CatalogEntry> entries) {
		Map<String, Availability> results = new ConcurrentHashMap<>();
		List<CompletableFuture<Void>> pending = new ArrayList<>();

		for (CatalogEntry entry : entries) {
			pending.add(availability(entry.slug())
				.thenAccept(availability -> results.put(entry.modId(), availability)));
		}

		return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
			.thenApply(ignored -> Map.copyOf(results));
	}

	private static Availability lookup(String slug) {
		String target = gameVersion();
		try {
			String url = API + "/project/" + URLEncoder.encode(slug, StandardCharsets.UTF_8) + "/version"
				+ "?loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
				+ "&game_versions=" + URLEncoder.encode("[\"" + target + "\"]", StandardCharsets.UTF_8);

			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(12))
				.GET()
				.build();

			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 404) {
				return new Availability(Availability.Status.NOT_FOUND, null,
					"El proyecto '" + slug + "' no existe en Modrinth.");
			}
			if (response.statusCode() != 200) {
				return new Availability(Availability.Status.OFFLINE, null,
					"Modrinth respondio " + response.statusCode() + ".");
			}

			JsonElement parsed = JsonParser.parseString(response.body());
			if (!parsed.isJsonArray()) {
				return new Availability(Availability.Status.OFFLINE, null, "Respuesta inesperada de Modrinth.");
			}

			JsonArray versions = parsed.getAsJsonArray();
			if (versions.isEmpty()) {
				// The decisive case: the project exists but has nothing for this Minecraft version.
				return new Availability(Availability.Status.NO_BUILD_FOR_VERSION, null,
					"No hay version de " + slug + " para Minecraft " + target + ".");
			}

			List<ModrinthVersion> candidates = new ArrayList<>();
			for (JsonElement element : versions) {
				ModrinthVersion version = parseVersion(element.getAsJsonObject(), target);
				if (version != null) {
					candidates.add(version);
				}
			}
			if (candidates.isEmpty()) {
				return new Availability(Availability.Status.NO_BUILD_FOR_VERSION, null,
					"No hay archivo descargable de " + slug + " para Minecraft " + target + ".");
			}

			// Most trustworthy channel first, then the newest build within it.
			candidates.sort(Comparator
				.comparingInt((ModrinthVersion version) -> version.channel().rank())
				.thenComparing(ModrinthVersion::versionNumber, Comparator.reverseOrder()));

			ModrinthVersion best = candidates.get(0);
			return new Availability(Availability.Status.COMPATIBLE, best,
				"Compatible con " + target + " (" + best.channel().label() + ").");
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Modrinth lookup failed for {}", slug, exception);
			return new Availability(Availability.Status.OFFLINE, null,
				"No se pudo consultar Modrinth (sin conexion).");
		}
	}

	private static ModrinthVersion parseVersion(JsonObject version, String target) {
		// Belt and braces: re-check the declared game versions even though the API filtered for us.
		JsonArray gameVersions = version.getAsJsonArray("game_versions");
		boolean declaresTarget = false;
		if (gameVersions != null) {
			for (JsonElement element : gameVersions) {
				if (target.equals(element.getAsString())) {
					declaresTarget = true;
					break;
				}
			}
		}
		if (!declaresTarget) {
			return null;
		}

		JsonArray files = version.getAsJsonArray("files");
		if (files == null || files.isEmpty()) {
			return null;
		}

		JsonObject chosen = null;
		for (JsonElement element : files) {
			JsonObject file = element.getAsJsonObject();
			if (file.has("primary") && file.get("primary").getAsBoolean()) {
				chosen = file;
				break;
			}
		}
		if (chosen == null) {
			chosen = files.get(0).getAsJsonObject();
		}

		JsonObject hashes = chosen.getAsJsonObject("hashes");
		String sha512 = hashes != null && hashes.has("sha512") ? hashes.get("sha512").getAsString() : "";
		if (sha512.isEmpty()) {
			// Without a hash there is no way to verify the download, so the build is not offered.
			return null;
		}

		List<String> required = new ArrayList<>();
		JsonArray dependencies = version.getAsJsonArray("dependencies");
		if (dependencies != null) {
			for (JsonElement element : dependencies) {
				JsonObject dependency = element.getAsJsonObject();
				boolean isRequired = dependency.has("dependency_type")
					&& "required".equals(dependency.get("dependency_type").getAsString());
				if (isRequired && dependency.has("project_id") && !dependency.get("project_id").isJsonNull()) {
					required.add(dependency.get("project_id").getAsString());
				}
			}
		}

		return new ModrinthVersion(
			version.has("project_id") ? version.get("project_id").getAsString() : "",
			version.has("version_number") ? version.get("version_number").getAsString() : "",
			ModrinthVersion.Channel.parse(
				version.has("version_type") ? version.get("version_type").getAsString() : null),
			chosen.get("filename").getAsString(),
			chosen.get("url").getAsString(),
			sha512,
			chosen.has("size") ? chosen.get("size").getAsLong() : 0L,
			target,
			List.copyOf(required)
		);
	}

	/**
	 * Global mod search, filtered server-side to Fabric builds for the running Minecraft version, so
	 * everything the player sees in the search tab is genuinely installable.
	 *
	 * @param offset paging offset; Modrinth caps {@code limit} at 100
	 */
	public static CompletableFuture<List<ModrinthProject>> search(String query, int offset, int limit) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String facets = "[[\"categories:fabric\"],[\"versions:" + gameVersion() + "\"],[\"project_type:mod\"]]";
				StringBuilder url = new StringBuilder(API + "/search?facets=")
					.append(URLEncoder.encode(facets, StandardCharsets.UTF_8))
					.append("&limit=").append(Math.max(1, Math.min(limit, 100)))
					.append("&offset=").append(Math.max(0, offset));
				if (query != null && !query.isBlank()) {
					url.append("&query=").append(URLEncoder.encode(query.strip(), StandardCharsets.UTF_8));
				} else {
					// With no query, show the most installed mods first rather than an arbitrary order.
					url.append("&index=downloads");
				}

				JsonObject root = getJson(url.toString()).getAsJsonObject();
				JsonArray hits = root.getAsJsonArray("hits");
				if (hits == null) {
					return List.<ModrinthProject>of();
				}

				List<ModrinthProject> projects = new ArrayList<>(hits.size());
				for (JsonElement element : hits) {
					JsonObject hit = element.getAsJsonObject();
					projects.add(new ModrinthProject(
						string(hit, "project_id"),
						string(hit, "slug"),
						string(hit, "title"),
						string(hit, "description"),
						// Left blank on purpose: the WebP thumbnail cannot be decoded and the PNG name
						// is not predictable. Callers resolve real icons through projects(...).
						"",
						strings(hit, "categories"),
						hit.has("downloads") ? hit.get("downloads").getAsInt() : 0,
						string(hit, "client_side"),
						string(hit, "server_side"),
						"",
						"",
						List.of()
					));
				}
				return List.copyOf(projects);
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Modrinth search failed for '{}'", query, exception);
				// Propagated on purpose: an empty list means "no matches", not "no connection",
				// and the UI shows a very different thing for each.
				throw new java.util.concurrent.CompletionException(exception);
			}
		});
	}

	/**
	 * Fetches many projects in one request. Used to get real icons for a whole list at once.
	 *
	 * <p>This is also the only reliable source for the icon URL: search hits only carry the WebP
	 * thumbnail, and the PNG is not always named {@code icon.png} — Mod Menu's, for instance, is a
	 * content hash. Guessing the filename 404s on those, so the URL is taken as given here.
	 */
	public static CompletableFuture<Map<String, ModrinthProject>> projects(List<String> projectIds) {
		if (projectIds == null || projectIds.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}

		return CompletableFuture.supplyAsync(() -> {
			Map<String, ModrinthProject> byId = new ConcurrentHashMap<>();
			try {
				StringBuilder ids = new StringBuilder("[");
				for (int index = 0; index < projectIds.size(); index++) {
					if (index > 0) {
						ids.append(',');
					}
					ids.append('"').append(projectIds.get(index)).append('"');
				}
				ids.append(']');

				String url = API + "/projects?ids=" + URLEncoder.encode(ids.toString(), StandardCharsets.UTF_8);
				JsonElement parsed = getJson(url);
				if (!parsed.isJsonArray()) {
					return Map.<String, ModrinthProject>of();
				}

				for (JsonElement element : parsed.getAsJsonArray()) {
					JsonObject project = element.getAsJsonObject();
					ModrinthProject converted = new ModrinthProject(
						string(project, "id"),
						string(project, "slug"),
						string(project, "title"),
						string(project, "description"),
						string(project, "raw_icon_url"),
						strings(project, "categories"),
						project.has("downloads") ? project.get("downloads").getAsInt() : 0,
						string(project, "client_side"),
						string(project, "server_side"),
						string(project, "source_url"),
						"",
						readLinks(project)
					);
					byId.put(converted.projectId(), converted);
					byId.put(converted.slug(), converted);
				}
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Bulk project lookup failed", exception);
			}
			return Map.copyOf(byId);
		});
	}

	/** Full project details including the README markdown shown in the install dialog. */
	public static CompletableFuture<ModrinthProject> details(String slug) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				JsonObject project = getJson(API + "/project/"
					+ URLEncoder.encode(slug, StandardCharsets.UTF_8)).getAsJsonObject();
				return new ModrinthProject(
					string(project, "id"),
					string(project, "slug"),
					string(project, "title"),
					string(project, "description"),
					// raw_icon_url is the original PNG; icon_url is a WebP that STB cannot decode.
					string(project, "raw_icon_url"),
					strings(project, "categories"),
					project.has("downloads") ? project.get("downloads").getAsInt() : 0,
					string(project, "client_side"),
					string(project, "server_side"),
					string(project, "source_url"),
					string(project, "body"),
					readLinks(project)
				);
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Modrinth details failed for {}", slug, exception);
				return null;
			}
		});
	}

	/** Downloads arbitrary bytes (used for project icons), capped so a bad URL cannot exhaust memory. */
	public static byte[] fetchBytes(String url, int maxBytes) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", USER_AGENT)
			.timeout(Duration.ofSeconds(12))
			.GET()
			.build();
		HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
		}
		byte[] body = response.body();
		if (body.length > maxBytes) {
			throw new IllegalStateException("Response larger than " + maxBytes + " bytes");
		}
		return body;
	}

	private static JsonElement getJson(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.timeout(Duration.ofSeconds(12))
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Modrinth responded " + response.statusCode());
		}
		return JsonParser.parseString(response.body());
	}

	/**
	 * Collects every author link a project exposes. Modrinth keeps the well-known ones as their own
	 * fields and the rest under {@code donation_urls}, whose {@code id} names the platform.
	 */
	private static List<ProjectLink> readLinks(JsonObject project) {
		List<ProjectLink> links = new ArrayList<>();

		addLink(links, ProjectLink.of(LinkIcons.Kind.SOURCE, "Codigo fuente", string(project, "source_url")));
		addLink(links, ProjectLink.of(LinkIcons.Kind.ISSUES, "Reportar un fallo", string(project, "issues_url")));
		addLink(links, ProjectLink.of(LinkIcons.Kind.WIKI, "Documentacion", string(project, "wiki_url")));
		addLink(links, ProjectLink.of(LinkIcons.Kind.DISCORD, "Discord", string(project, "discord_url")));

		JsonArray donations = project.getAsJsonArray("donation_urls");
		if (donations != null) {
			for (JsonElement element : donations) {
				JsonObject donation = element.getAsJsonObject();
				String donationUrl = string(donation, "url");
				LinkIcons.Kind kind = LinkIcons.Kind.fromDonation(string(donation, "id"), donationUrl);
				String platform = string(donation, "platform");
				addLink(links, ProjectLink.of(kind, platform.isBlank() ? kind.label() : platform, donationUrl));
			}
		}
		return List.copyOf(links);
	}

	private static void addLink(List<ProjectLink> links, ProjectLink link) {
		if (link != null) {
			links.add(link);
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static List<String> strings(JsonObject object, String key) {
		JsonArray array = object.getAsJsonArray(key);
		if (array == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				values.add(element.getAsString());
			}
		}
		return List.copyOf(values);
	}

	/** Clears the session cache so a retry can pick up a restored connection. */
	public static void invalidate() {
		CACHE.clear();
	}

	public static Optional<ModrinthVersion> cachedVersion(String slug) {
		CompletableFuture<Availability> future = CACHE.get(slug);
		if (future == null || !future.isDone()) {
			return Optional.empty();
		}
		Availability availability = future.join();
		return availability.isInstallable() ? Optional.of(availability.version()) : Optional.empty();
	}
}

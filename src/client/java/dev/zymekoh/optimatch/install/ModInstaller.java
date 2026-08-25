package dev.zymekoh.optimatch.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.catalog.ModrinthVersion;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads mods into {@code mods/}.
 *
 * <p>Three guards make this safe to run against a live instance:
 * <ul>
 *   <li>Every file is checked against the SHA-512 Modrinth publishes. A mismatch deletes the
 *       partial download and aborts.</li>
 *   <li>The downloaded jar's own {@code fabric.mod.json} is read <em>before</em> it is put in place.
 *       If that mod id is already loaded, the file is discarded — two jars declaring the same id
 *       make Fabric refuse to start, which is exactly the breakage this tool exists to prevent.</li>
 *   <li>An existing jar is never overwritten. On Windows the loader holds open handles on the jars
 *       in {@code mods/}; writing a brand new file is fine, replacing one is not.</li>
 * </ul>
 */
public final class ModInstaller {
	private static final int MAX_DEPENDENCY_DEPTH = 4;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private ModInstaller() {
	}

	/** Progress of a single file, reported to the UI. */
	public record Progress(String fileName, Stage stage, long bytesRead, long totalBytes, String message) {
		public enum Stage {
			DOWNLOADING, VERIFYING, DONE, FAILED, SKIPPED_ALREADY_INSTALLED
		}

		public float fraction() {
			return this.totalBytes <= 0 ? 0.0F : Math.min(1.0F, this.bytesRead / (float) this.totalBytes);
		}
	}

	public static Path modsDirectory() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	/**
	 * Works out every jar an install would need, without downloading anything.
	 *
	 * <p>Dependencies come back from Modrinth as project ids, and each one goes through the same
	 * version gate as the mod itself. If any required dependency has no build for this Minecraft
	 * version the whole plan is blocked — a half-installed dependency chain is worse than nothing.
	 */
	public static CompletableFuture<InstallPlan> plan(String slug, String displayName) {
		return CompletableFuture.supplyAsync(() -> {
			List<ModrinthVersion> files = new ArrayList<>();
			List<String> blockers = new ArrayList<>();
			Set<String> visited = new HashSet<>();

			resolve(slug, displayName, files, blockers, visited, 0);

			return new InstallPlan(slug, displayName, List.copyOf(files), List.copyOf(blockers));
		});
	}

	private static void resolve(String slug, String label, List<ModrinthVersion> files,
								List<String> blockers, Set<String> visited, int depth) {
		if (!visited.add(slug)) {
			return;
		}
		if (depth > MAX_DEPENDENCY_DEPTH) {
			blockers.add("Cadena de dependencias demasiado profunda en " + label + ".");
			return;
		}

		ModrinthClient.Availability availability = ModrinthClient.availability(slug).join();
		if (!availability.isInstallable()) {
			blockers.add(label + ": " + availability.detail());
			return;
		}

		ModrinthVersion version = availability.version();
		files.add(version);

		for (String dependencyProjectId : version.requiredDependencies()) {
			// The project endpoint accepts an id as well as a slug, so the id can be used directly.
			resolve(dependencyProjectId, dependencyProjectId, files, blockers, visited, depth + 1);
		}
	}

	/**
	 * Executes a plan. Does nothing at all if the plan is blocked.
	 *
	 * @param listener called on the calling (background) thread for every progress change
	 */
	public static CompletableFuture<Map<String, Progress>> execute(InstallPlan plan, Consumer<Progress> listener) {
		return CompletableFuture.supplyAsync(() -> {
			Map<String, Progress> results = new LinkedHashMap<>();
			if (plan.isBlocked()) {
				return results;
			}

			Path mods = modsDirectory();
			try {
				Files.createDirectories(mods);
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.error("Could not create the mods directory", exception);
				return results;
			}

			for (ModrinthVersion version : plan.files()) {
				Progress progress = download(version, mods, listener);
				results.put(version.fileName(), progress);
				if (progress.stage() == Progress.Stage.FAILED) {
					// Stop at the first failure: a partial dependency chain is not worth continuing.
					break;
				}
			}
			return results;
		});
	}

	private static Progress download(ModrinthVersion version, Path mods, Consumer<Progress> listener) {
		Path target = mods.resolve(version.fileName());
		Path partial = mods.resolve(version.fileName() + ".part");

		if (Files.exists(target)) {
			Progress skipped = new Progress(version.fileName(), Progress.Stage.SKIPPED_ALREADY_INSTALLED,
				0, 0, "Ya existe ese archivo en mods/.");
			listener.accept(skipped);
			return skipped;
		}

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(version.downloadUrl()))
				.header("User-Agent", "KoHs-OptiMatch/0.1.0 (Minecraft Fabric mod selector)")
				.timeout(Duration.ofMinutes(3))
				.GET()
				.build();

			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				return fail(version, partial, listener, "Descarga fallida (HTTP " + response.statusCode() + ").");
			}

			long total = version.fileSize();
			long read = 0;
			MessageDigest digest = MessageDigest.getInstance("SHA-512");

			try (InputStream in = response.body();
				 var out = Files.newOutputStream(partial)) {
				byte[] buffer = new byte[64 * 1024];
				int count;
				while ((count = in.read(buffer)) > 0) {
					out.write(buffer, 0, count);
					digest.update(buffer, 0, count);
					read += count;
					listener.accept(new Progress(version.fileName(), Progress.Stage.DOWNLOADING, read, total, ""));
				}
			}

			listener.accept(new Progress(version.fileName(), Progress.Stage.VERIFYING, read, total, ""));

			String actual = toHex(digest.digest());
			if (!actual.equalsIgnoreCase(version.sha512())) {
				return fail(version, partial, listener,
					"El hash SHA-512 no coincide. Archivo descartado.");
			}

			// Reading the id out of the downloaded jar is the only exact duplicate check available.
			String modId = readModId(partial);
			if (modId != null && FabricLoader.getInstance().isModLoaded(modId)) {
				Files.deleteIfExists(partial);
				Progress skipped = new Progress(version.fileName(), Progress.Stage.SKIPPED_ALREADY_INSTALLED,
					read, total, "Ya tienes '" + modId + "' cargado; instalarlo otra vez romperia el arranque.");
				listener.accept(skipped);
				return skipped;
			}

			Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
			PendingChanges.add(version.fileName(), version.fileName(), version.versionNumber());

			Progress done = new Progress(version.fileName(), Progress.Stage.DONE, read, total,
				"Listo para el proximo arranque.");
			listener.accept(done);
			OptiMatchClient.LOGGER.info("Installed {} ({}), active after restart", version.fileName(), modId);
			return done;
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.error("Download of {} failed", version.fileName(), exception);
			return fail(version, partial, listener, "Error de descarga: " + exception.getMessage());
		}
	}

	private static Progress fail(ModrinthVersion version, Path partial, Consumer<Progress> listener, String message) {
		try {
			Files.deleteIfExists(partial);
		} catch (Exception ignored) {
			// Nothing more to do: the partial file is already unusable.
		}
		Progress failure = new Progress(version.fileName(), Progress.Stage.FAILED, 0, 0, message);
		listener.accept(failure);
		return failure;
	}

	/** Reads {@code fabric.mod.json} straight out of the downloaded jar. */
	private static String readModId(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return null;
			}
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed.isJsonObject() && parsed.getAsJsonObject().has("id")) {
					return parsed.getAsJsonObject().get("id").getAsString();
				}
			}
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Could not read the mod id from {}", jar, exception);
		}
		return null;
	}

	private static String toHex(byte[] bytes) {
		StringBuilder hex = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			hex.append(Character.forDigit((value >> 4) & 0xF, 16));
			hex.append(Character.forDigit(value & 0xF, 16));
		}
		return hex.toString();
	}
}

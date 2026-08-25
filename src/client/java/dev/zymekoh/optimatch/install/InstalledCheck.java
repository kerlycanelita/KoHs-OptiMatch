package dev.zymekoh.optimatch.install;

import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.ModScanner;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Whether a mod is already present, so the interface can grey out its download button instead of
 * offering an install that would be refused.
 *
 * <p>Two independent checks, because either can be true on its own: the mod may be loaded right now,
 * or its jar may already be sitting in {@code mods/} waiting for the next restart — which is exactly
 * the state the installer leaves things in.
 */
public final class InstalledCheck {
	private InstalledCheck() {
	}

	/** The reason a download is not on offer, or {@code null} when it is. */
	public enum State {
		/** Loaded in this session. */
		LOADED,
		/** Downloaded and waiting for the next launch. */
		PENDING_RESTART
	}

	/**
	 * @param slug  Modrinth slug, matched loosely against mod ids
	 * @param modId the Fabric mod id when known, which is the exact match
	 */
	public static State stateOf(String slug, String modId) {
		if (modId != null && !modId.isBlank() && isLoaded(modId)) {
			return State.LOADED;
		}
		if (slug != null && !slug.isBlank() && isLoaded(slug)) {
			return State.LOADED;
		}
		return isPending(slug, modId) ? State.PENDING_RESTART : null;
	}

	public static boolean isInstalled(String slug, String modId) {
		return stateOf(slug, modId) != null;
	}

	/** Human wording for the greyed-out button. */
	public static String label(State state) {
		return state == State.PENDING_RESTART ? "Descargado" : "Ya lo tienes";
	}

	public static String explanation(State state) {
		return state == State.PENDING_RESTART
			? "Ya esta en tu carpeta mods/, esperando al proximo arranque para activarse."
			: "Este mod ya esta cargado en tu instancia.";
	}

	private static boolean isLoaded(String candidate) {
		String normalised = normalise(candidate);
		for (InstalledMod mod : ModScanner.scan()) {
			if (normalise(mod.id()).equals(normalised)) {
				return true;
			}
		}
		return false;
	}

	/** A jar downloaded this session, or any jar in mods/ whose name carries the slug. */
	private static boolean isPending(String slug, String modId) {
		for (PendingChanges.Entry entry : PendingChanges.entries()) {
			String fileName = normalise(entry.fileName());
			if (slug != null && !slug.isBlank() && fileName.contains(normalise(slug))) {
				return true;
			}
			if (modId != null && !modId.isBlank() && fileName.contains(normalise(modId))) {
				return true;
			}
		}

		// Also covers jars put there by an earlier session, which PendingChanges does not remember.
		try (var files = Files.list(ModInstaller.modsDirectory())) {
			String slugKey = slug == null ? "" : normalise(slug);
			String idKey = modId == null ? "" : normalise(modId);
			return files.anyMatch(path -> {
				String name = normalise(path.getFileName().toString());
				if (!name.endsWith("jar")) {
					return false;
				}
				return !slugKey.isBlank() && name.contains(slugKey)
					|| !idKey.isBlank() && name.contains(idKey);
			});
		} catch (Exception exception) {
			return false;
		}
	}

	private static String normalise(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}

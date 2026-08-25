package dev.zymekoh.optimatch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's own settings, kept in {@code config/kohs_optimatch/settings.json}.
 *
 * <p>Deliberately tiny: the only thing worth remembering is whether the player wants the selector to
 * greet them on every launch. Everything else about the tool is derived at runtime.
 */
public final class OptiMatchSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Whether the selector opens by itself before the main menu. */
	private static boolean showOnStartup = true;

	/**
	 * Whether the player has been asked yet. The preference prompt appears once, on the first run —
	 * asking on every launch would be exactly the nuisance the setting exists to prevent.
	 */
	private static boolean startupPromptAnswered;

	private static boolean loaded;

	private OptiMatchSettings() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir()
			.resolve(OptiMatchClient.MOD_ID)
			.resolve("settings.json");
	}

	public static boolean showOnStartup() {
		ensureLoaded();
		return showOnStartup;
	}

	public static boolean startupPromptAnswered() {
		ensureLoaded();
		return startupPromptAnswered;
	}

	/** Records the player's answer to the first-run question and writes it straight away. */
	public static void answerStartupPrompt(boolean keepShowing) {
		ensureLoaded();
		showOnStartup = keepShowing;
		startupPromptAnswered = true;
		save();
	}

	/** Lets the setting be flipped later without going through the prompt again. */
	public static void setShowOnStartup(boolean value) {
		ensureLoaded();
		showOnStartup = value;
		save();
	}

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;

		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("showOnStartup")) {
				showOnStartup = root.get("showOnStartup").getAsBoolean();
			}
			if (root.has("startupPromptAnswered")) {
				startupPromptAnswered = root.get("startupPromptAnswered").getAsBoolean();
			}
		} catch (Exception exception) {
			// A corrupt settings file falls back to the defaults rather than blocking startup.
			OptiMatchClient.LOGGER.warn("Could not read settings, using defaults", exception);
		}
	}

	private static void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("showOnStartup", showOnStartup);
			root.addProperty("startupPromptAnswered", startupPromptAnswered);
			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.error("Could not save settings", exception);
		}
	}
}

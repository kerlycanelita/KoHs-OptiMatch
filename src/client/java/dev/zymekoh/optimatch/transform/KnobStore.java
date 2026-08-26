package dev.zymekoh.optimatch.transform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and writes the mods' own configuration files.
 *
 * <p>Everything goes through the file each mod already reads at startup, never through Mixin itself.
 * A measurement on a live instance settled that: emptying a prepared mixin config left ModMenu's
 * injections applied anyway, so the only changes that actually hold are the ones the owning mod
 * agreed to honour.
 */
public final class KnobStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private KnobStore() {
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static Path fileFor(MixinKnob knob) {
		return switch (knob.modId()) {
			case "sodium" -> configDir().resolve("sodium-mixins.properties");
			case "immediatelyfast" -> configDir().resolve("immediatelyfast.json");
			default -> configDir().resolve(knob.modId() + ".json");
		};
	}

	/** Current effective value, falling back to the knob's default when nothing is written. */
	public static boolean read(MixinKnob knob) {
		try {
			if (knob.kind() == MixinKnob.Kind.RULE) {
				String found = properties(fileFor(knob)).get(knob.id());
				return found == null ? knob.defaultOn() : !"false".equalsIgnoreCase(found.trim());
			}
			JsonObject json = json(fileFor(knob));
			return json != null && json.has(knob.id())
				? json.get(knob.id()).getAsBoolean()
				: knob.defaultOn();
		} catch (Exception e) {
			return knob.defaultOn();
		}
	}

	/** True when the player has written an explicit value, i.e. this is no longer the mod's default. */
	public static boolean isOverridden(MixinKnob knob) {
		try {
			if (knob.kind() == MixinKnob.Kind.RULE) {
				return properties(fileFor(knob)).containsKey(knob.id());
			}
			return read(knob) != knob.defaultOn();
		} catch (Exception e) {
			return false;
		}
	}

	public static void write(MixinKnob knob, boolean value) throws IOException {
		Path file = fileFor(knob);
		backupOnce(file);
		if (knob.kind() == MixinKnob.Kind.RULE) {
			writeRule(file, knob.id(), value);
		} else {
			writeFlag(file, knob.id(), value);
		}
	}

	/** Removes an explicit override so the mod goes back to deciding for itself. */
	public static void reset(MixinKnob knob) throws IOException {
		Path file = fileFor(knob);
		if (!Files.exists(file)) {
			return;
		}
		backupOnce(file);
		if (knob.kind() == MixinKnob.Kind.RULE) {
			List<String> kept = new ArrayList<>();
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (!line.strip().startsWith(knob.id() + "=")) {
					kept.add(line);
				}
			}
			atomicWrite(file, String.join(System.lineSeparator(), kept) + System.lineSeparator());
		} else {
			writeFlag(file, knob.id(), knob.defaultOn());
		}
	}

	/**
	 * Rewrites one rule line in place.
	 *
	 * <p>Line-based rather than {@link java.util.Properties}, which would drop the header comment
	 * pointing at the mod's own documentation and reshuffle everything the player wrote by hand.
	 */
	private static void writeRule(Path file, String rule, boolean value) throws IOException {
		List<String> lines = Files.exists(file)
			? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
			: new ArrayList<>();
		String entry = rule + "=" + value;
		boolean replaced = false;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).strip().startsWith(rule + "=")) {
				lines.set(i, entry);
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			lines.add(entry);
		}
		atomicWrite(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
	}

	private static void writeFlag(Path file, String field, boolean value) throws IOException {
		JsonObject json = json(file);
		if (json == null) {
			// Refuse to invent a config file: the mod owns its schema and defaults.
			throw new IOException("No existe " + file.getFileName() + "; abre el mod una vez para generarlo.");
		}
		json.addProperty(field, value);
		atomicWrite(file, GSON.toJson(json));
	}

	private static java.util.Map<String, String> properties(Path file) throws IOException {
		java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
		if (!Files.exists(file)) {
			return out;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String stripped = line.strip();
			if (stripped.isEmpty() || stripped.startsWith("#")) {
				continue;
			}
			int eq = stripped.indexOf('=');
			if (eq > 0) {
				out.put(stripped.substring(0, eq).strip(), stripped.substring(eq + 1).strip());
			}
		}
		return out;
	}

	private static JsonObject json(Path file) {
		try {
			if (!Files.exists(file)) {
				return null;
			}
			return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** One backup per file, taken before the first edit, so the pristine state stays recoverable. */
	private static void backupOnce(Path file) {
		Path backup = file.resolveSibling(file.getFileName() + ".optimatch.bak");
		if (Files.exists(file) && !Files.exists(backup)) {
			try {
				Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (IOException e) {
				OptiMatchClient.LOGGER.warn("Could not back up {}", file, e);
			}
		}
	}

	private static void atomicWrite(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".optimatch.tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}

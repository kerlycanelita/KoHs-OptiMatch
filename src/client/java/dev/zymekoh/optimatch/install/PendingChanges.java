package dev.zymekoh.optimatch.install;

import java.util.ArrayList;
import java.util.List;

/**
 * Mods downloaded during this session that will only become active on the next launch.
 *
 * <p>Fabric freezes its mod list before Minecraft starts ({@code FabricLoaderImpl.freeze()} is
 * called from {@code Knot} during launch, and {@code load()} afterwards throws
 * <em>"Frozen - cannot load additional mods!"</em>), and mixins are applied while classes are being
 * loaded. There is no supported way to activate a mod in a running instance, so the honest thing is
 * to track what is waiting and make restarting a single click.
 */
public final class PendingChanges {
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private PendingChanges() {
	}

	/** @param fileName the jar that now sits in {@code mods/} */
	public record Entry(String displayName, String fileName, String versionNumber) {
	}

	public static synchronized void add(String displayName, String fileName, String versionNumber) {
		if (ENTRIES.stream().noneMatch(entry -> entry.fileName().equals(fileName))) {
			ENTRIES.add(new Entry(displayName, fileName, versionNumber));
		}
	}

	public static synchronized List<Entry> entries() {
		return List.copyOf(ENTRIES);
	}

	public static synchronized int count() {
		return ENTRIES.size();
	}

	public static synchronized boolean isEmpty() {
		return ENTRIES.isEmpty();
	}
}

package dev.zymekoh.optimatch.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.scan.InstalledMod;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Loads each installed mod's own icon out of its jar and draws it at list size.
 *
 * <p>Icons are uploaded lazily on the render thread and cached for the session. A mod that ships no
 * icon — or one that fails to decode — falls back to a tinted tile with its initial, so every row
 * lines up whether or not an image exists.
 */
public final class ModIcons {
	private static final Map<String, Identifier> LOADED = new ConcurrentHashMap<>();
	private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
	/** Guards against firing the same remote download twice while it is in flight. */
	private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

	private ModIcons() {
	}

	/**
	 * Uploads the icons for the given mods. Safe to call repeatedly: each mod is only read once.
	 * Must run on the render thread, since it touches the texture manager.
	 */
	public static void preload(List<InstalledMod> mods) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		for (InstalledMod mod : mods) {
			if (!LOADED.containsKey(mod.id()) && !FAILED.contains(mod.id())) {
				upload(minecraft, mod);
			}
		}
	}

	private static void upload(Minecraft minecraft, InstalledMod mod) {
		String iconPath = mod.iconPath();
		if (iconPath == null || iconPath.isBlank() || mod.rootPaths().isEmpty()) {
			FAILED.add(mod.id());
			return;
		}

		Path resolved = null;
		for (Path root : mod.rootPaths()) {
			try {
				Path candidate = root.resolve(iconPath.replace("/", root.getFileSystem().getSeparator()));
				if (Files.exists(candidate)) {
					resolved = candidate;
					break;
				}
			} catch (Exception ignored) {
				// Odd separators inside a jar filesystem: just try the next root.
			}
		}
		if (resolved == null) {
			FAILED.add(mod.id());
			return;
		}

		NativeImage image = null;
		try (InputStream stream = Files.newInputStream(resolved)) {
			image = NativeImage.read(stream);
			Identifier identifier = Identifier.fromNamespaceAndPath(
				OptiMatchClient.MOD_ID, "mod_icon/" + sanitize(mod.id()));
			// DynamicTexture takes ownership of the image, so it must not be closed here.
			minecraft.getTextureManager().register(identifier,
				new DynamicTexture(() -> "KoHs OptiMatch icon for " + mod.id(), image));
			LOADED.put(mod.id(), identifier);
		} catch (Exception exception) {
			if (image != null) {
				image.close();
			}
			FAILED.add(mod.id());
			OptiMatchClient.LOGGER.debug("Could not load the icon for {}", mod.id(), exception);
		}
	}

	/**
	 * Draws the icon for {@code modId} at {@code size} px, or a lettered fallback tile.
	 *
	 * @param displayName used for the fallback initial
	 * @param accent      fallback tile colour, normally the mod's role colour
	 * @param opacity     screen-wide fade multiplier
	 */
	public static void draw(GuiGraphicsExtractor graphics, Font font, String modId, String displayName,
							int x, int y, int size, int accent, float opacity) {
		Identifier icon = LOADED.get(modId);
		if (icon != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, size, size, size, size);
			return;
		}
		drawFallback(graphics, font, displayName, x, y, size, accent, opacity);
	}

	/** Lettered tile used when a mod ships no icon, and for catalog mods that are not installed yet. */
	public static void drawFallback(GuiGraphicsExtractor graphics, Font font, String displayName,
									int x, int y, int size, int accent, float opacity) {
		int rgb = accent & 0xFFFFFF;
		Draw.roundedRect(graphics, x, y, size, size, 2, Theme.argb(Math.round(150 * opacity), Theme.darken(rgb, 0.6F)));
		Draw.outline(graphics, x, y, size, size, Theme.argb(Math.round(190 * opacity), rgb));

		String initial = displayName == null || displayName.isBlank()
			? "?"
			: displayName.substring(0, 1).toUpperCase(Locale.ROOT);
		if (size >= 10) {
			graphics.centeredText(font, initial, x + size / 2, y + (size - font.lineHeight) / 2 + 1,
				Theme.withAlpha(Theme.TEXT, opacity));
		}
	}

	/**
	 * Fetches a Modrinth project icon so search results can show the real artwork.
	 *
	 * <p>The URL must be the PNG one ({@code raw_icon_url}); Modrinth's {@code icon_url} is a WebP
	 * and {@code NativeImage.read} cannot decode it. Network and decode happen off-thread, and only
	 * the texture upload is bounced back onto the render thread.
	 */
	public static void requestRemote(String projectId, String iconUrl) {
		if (projectId == null || projectId.isBlank() || iconUrl == null || iconUrl.isBlank()) {
			return;
		}
		String key = "remote/" + projectId;
		if (LOADED.containsKey(key) || !REQUESTED.add(key)) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			try {
				byte[] bytes = ModrinthClient.fetchBytes(iconUrl, 2 * 1024 * 1024);
				NativeImage image = NativeImage.read(bytes);

				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft == null) {
					image.close();
					return;
				}
				minecraft.execute(() -> {
					try {
						Identifier identifier = Identifier.fromNamespaceAndPath(
							OptiMatchClient.MOD_ID, "remote_icon/" + sanitize(projectId));
						minecraft.getTextureManager().register(identifier,
							new DynamicTexture(() -> "KoHs OptiMatch remote icon " + projectId, image));
						LOADED.put(key, identifier);
					} catch (Exception exception) {
						image.close();
						FAILED.add(key);
					}
				});
			} catch (Exception exception) {
				FAILED.add(key);
				OptiMatchClient.LOGGER.debug("Could not fetch the remote icon for {}", projectId, exception);
			}
		});
	}

	/** Draws a Modrinth project icon, falling back to a lettered tile while it loads or if it fails. */
	public static void drawRemote(GuiGraphicsExtractor graphics, Font font, String projectId, String title,
								  int x, int y, int size, int accent, float opacity) {
		Identifier icon = LOADED.get("remote/" + projectId);
		if (icon != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, size, size, size, size);
			return;
		}
		drawFallback(graphics, font, title, x, y, size, accent, opacity);
	}

	public static void clear(Minecraft minecraft) {
		if (minecraft != null) {
			for (Identifier identifier : LOADED.values()) {
				minecraft.getTextureManager().release(identifier);
			}
		}
		LOADED.clear();
		FAILED.clear();
		REQUESTED.clear();
	}

	private static String sanitize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
	}
}

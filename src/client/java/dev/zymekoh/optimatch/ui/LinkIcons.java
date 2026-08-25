package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small glyphs for the author links Modrinth exposes on a project.
 *
 * <p>These are drawn from rectangles rather than shipped as images: bundling third-party logos
 * raises trademark questions, and hand-drawn marks scale cleanly at any {@link UiScale} without an
 * asset. Each one only has to be recognisable next to its own label.
 */
public final class LinkIcons {
	/** The link kinds Modrinth can return for a project. */
	public enum Kind {
		SOURCE("Codigo fuente", 0xFFB9C4D4),
		ISSUES("Reportar un fallo", 0xFFFF9E7A),
		WIKI("Documentacion", 0xFF9CD2FF),
		DISCORD("Discord", 0xFF8B9CF7),
		KOFI("Ko-fi", 0xFFFF7A9A),
		BUY_ME_A_COFFEE("Buy Me a Coffee", 0xFFFFD866),
		PATREON("Patreon", 0xFFFF7A6B),
		PAYPAL("PayPal", 0xFF7FB2F0),
		GITHUB_SPONSORS("GitHub Sponsors", 0xFFF08FC4),
		OTHER("Enlace", 0xFFC3AAD6);

		private final String label;
		private final int color;

		Kind(String label, int color) {
			this.label = label;
			this.color = color;
		}

		public String label() {
			return this.label;
		}

		public int color() {
			return this.color;
		}

		/** Maps Modrinth's {@code donation_urls[].id} onto a glyph. */
		public static Kind fromDonationId(String id) {
			if (id == null) {
				return OTHER;
			}
			return switch (id.toLowerCase(java.util.Locale.ROOT)) {
				case "patreon" -> PATREON;
				case "bmac" -> BUY_ME_A_COFFEE;
				case "paypal" -> PAYPAL;
				case "github" -> GITHUB_SPONSORS;
				case "ko-fi", "kofi" -> KOFI;
				default -> OTHER;
			};
		}
	}

	private LinkIcons() {
	}

	/**
	 * Draws the glyph for {@code kind} in a {@code size}-pixel box.
	 *
	 * @param size side length; designed for 10-14
	 */
	public static void draw(GuiGraphicsExtractor graphics, Kind kind, int x, int y, int size, float opacity) {
		int color = Theme.withAlpha(kind.color(), opacity);
		switch (kind) {
			case SOURCE -> drawSource(graphics, x, y, size, color);
			case ISSUES -> drawIssues(graphics, x, y, size, color, opacity);
			case WIKI -> drawWiki(graphics, x, y, size, color, opacity);
			case DISCORD -> drawDiscord(graphics, x, y, size, color);
			case KOFI, BUY_ME_A_COFFEE -> drawCoffee(graphics, x, y, size, color, opacity);
			case PATREON -> drawPatreon(graphics, x, y, size, color);
			case PAYPAL -> drawCard(graphics, x, y, size, color, opacity);
			case GITHUB_SPONSORS -> drawHeart(graphics, x, y, size, color);
			case OTHER -> drawLink(graphics, x, y, size, color);
		}
	}

	/** Angle brackets, the universal shorthand for source code. */
	private static void drawSource(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		int mid = y + size / 2;
		int step = Math.max(1, size / 5);
		for (int i = 0; i < 3; i++) {
			g.fill(x + i * step, mid - i * step, x + i * step + step, mid - i * step + step, color);
			g.fill(x + i * step, mid + i * step, x + i * step + step, mid + i * step + step, color);
			int right = x + size - step - i * step;
			g.fill(right, mid - i * step, right + step, mid - i * step + step, color);
			g.fill(right, mid + i * step, right + step, mid + i * step + step, color);
		}
	}

	/** A ring with an exclamation inside: something to report. */
	private static void drawIssues(GuiGraphicsExtractor g, int x, int y, int size, int color, float opacity) {
		ring(g, x, y, size, color);
		int centerX = x + size / 2;
		g.fill(centerX, y + size / 4, centerX + 1, y + size / 2 + 1, color);
		g.fill(centerX, y + size - size / 4, centerX + 1, y + size - size / 4 + 1,
			Theme.withAlpha(0xFFFFFFFF, opacity));
	}

	/** An open book. */
	private static void drawWiki(GuiGraphicsExtractor g, int x, int y, int size, int color, float opacity) {
		int half = size / 2;
		g.fill(x, y + 1, x + half - 1, y + size - 1, color);
		g.fill(x + half + 1, y + 1, x + size, y + size - 1, color);
		// The spine gap, plus a couple of text ticks so it reads as pages.
		g.fill(x + 1, y + 3, x + half - 2, y + 4, Theme.withAlpha(0x33000000, opacity));
		g.fill(x + half + 2, y + 3, x + size - 1, y + 4, Theme.withAlpha(0x33000000, opacity));
	}

	/** A rounded speech bubble with a tail. */
	private static void drawDiscord(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		Draw.roundedRect(g, x, y + 1, size, size - 4, 2, color);
		g.fill(x + 2, y + size - 3, x + 5, y + size - 1, color);
	}

	/** A cup with a handle and a wisp of steam. */
	private static void drawCoffee(GuiGraphicsExtractor g, int x, int y, int size, int color, float opacity) {
		int cupTop = y + size / 3;
		g.fill(x + 1, cupTop, x + size - 3, y + size - 1, color);
		// Handle.
		g.fill(x + size - 3, cupTop + 1, x + size - 1, cupTop + 2, color);
		g.fill(x + size - 2, cupTop + 2, x + size - 1, cupTop + 4, color);
		g.fill(x + size - 3, cupTop + 4, x + size - 1, cupTop + 5, color);
		// Steam.
		int steam = Theme.withAlpha(0xFFFFFFFF, opacity * 0.5F);
		g.fill(x + 3, y, x + 4, cupTop - 1, steam);
		g.fill(x + 6, y + 1, x + 7, cupTop - 1, steam);
	}

	/** Patreon's mark: a circle beside a vertical bar. */
	private static void drawPatreon(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		g.fill(x, y, x + 2, y + size, color);
		int radius = (size - 2) / 2;
		int centerX = x + 4 + radius;
		int centerY = y + radius + 1;
		disc(g, centerX, centerY, radius, color);
	}

	/** A payment card. */
	private static void drawCard(GuiGraphicsExtractor g, int x, int y, int size, int color, float opacity) {
		Draw.roundedRect(g, x, y + 2, size, size - 4, 1, color);
		g.fill(x, y + 4, x + size, y + 6, Theme.withAlpha(0x55000000, opacity));
	}

	/** A heart, for sponsorship. */
	private static void drawHeart(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		int half = size / 2;
		g.fill(x + 1, y + 2, x + half, y + half + 2, color);
		g.fill(x + half + 1, y + 2, x + size - 1, y + half + 2, color);
		g.fill(x + 1, y + 1, x + half - 1, y + 2, color);
		g.fill(x + half + 2, y + 1, x + size - 1, y + 2, color);
		// Taper to a point.
		for (int row = 0; row < half; row++) {
			g.fill(x + 1 + row, y + half + 2 + row, x + size - 1 - row, y + half + 3 + row, color);
		}
	}

	/** Two interlocking chain links. */
	private static void drawLink(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		int half = size / 2;
		ring(g, x, y, half + 2, color);
		ring(g, x + half - 2, y + half - 2, half + 2, color);
	}

	private static void ring(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		g.fill(x + 1, y, x + size - 1, y + 1, color);
		g.fill(x + 1, y + size - 1, x + size - 1, y + size, color);
		g.fill(x, y + 1, x + 1, y + size - 1, color);
		g.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
	}

	private static void disc(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
			g.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
		}
	}
}

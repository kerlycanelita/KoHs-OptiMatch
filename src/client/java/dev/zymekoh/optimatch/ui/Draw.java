package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Small drawing helpers built on top of the 26.1 {@link GuiGraphicsExtractor} primitives.
 */
public final class Draw {
	private Draw() {
	}

	public static void rect(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		if (width > 0 && height > 0) {
			g.fill(x, y, x + width, y + height, color);
		}
	}

	public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		if (r == 0) {
			g.fill(x, y, x + width, y + height, color);
			return;
		}
		g.fill(x + r, y, x + width - r, y + height, color);
		g.fill(x, y + r, x + width, y + height - r, color);
		for (int dy = 0; dy < r; dy++) {
			int dx = (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
			g.fill(x + r - dx, y + dy, x + width - r + dx, y + dy + 1, color);
			g.fill(x + r - dx, y + height - dy - 1, x + width - r + dx, y + height - dy, color);
		}
	}

	/** Border + fill + drop shadow, the standard OptiMatch card. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int radius, int fill, int border) {
		roundedRect(g, x - 1, y + 2, width + 2, height + 2, radius + 1, Theme.SHADOW);
		roundedRect(g, x, y, width, height, radius, border);
		roundedRect(g, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int radius) {
		panel(g, x, y, width, height, radius, Theme.PANEL, Theme.BORDER_SOFT);
	}

	public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}
		g.fill(x, y, x + width, y + 1, color);
		g.fill(x, y + height - 1, x + width, y + height, color);
		g.fill(x, y, x + 1, y + height, color);
		g.fill(x + width - 1, y, x + width, y + height, color);
	}

	/** Horizontal progress / rating bar. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, float progress, int trackColor, int fillColor) {
		roundedRect(g, x, y, width, height, height / 2, trackColor);
		int filled = Math.round(width * Mth.clamp(progress, 0.0F, 1.0F));
		if (filled > 0) {
			roundedRect(g, x, y, Math.max(height, filled), height, height / 2, fillColor);
		}
	}

	/** Draws text clipped to {@code maxWidth}, appending an ellipsis when it does not fit. */
	public static void clippedText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color, boolean shadow) {
		if (maxWidth <= 0 || text == null || text.isEmpty()) {
			return;
		}
		String shown = text;
		if (font.width(text) > maxWidth) {
			shown = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
		}
		g.text(font, shown, x, y, color, shadow);
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	/**
	 * Word-wraps {@code text} across up to {@code maxLines}, adding an ellipsis if it does not fit.
	 *
	 * @return the y coordinate just below the last line drawn
	 */
	public static int wrappedText(GuiGraphicsExtractor g, Font font, String text, int x, int y,
								  int maxWidth, int maxLines, int color, float opacity) {
		if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
			return y;
		}

		int cursorY = y;
		String remaining = text.replaceAll("\\s+", " ").strip();
		for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
			boolean lastLine = line == maxLines - 1;

			if (font.width(remaining) <= maxWidth) {
				g.text(font, remaining, x, cursorY, Theme.withAlpha(color, opacity), false);
				return cursorY + 10;
			}

			if (lastLine) {
				// Out of room: truncate this last line rather than dropping the rest silently.
				clippedText(g, font, remaining, x, cursorY, maxWidth, Theme.withAlpha(color, opacity), false);
				return cursorY + 10;
			}

			String head = font.plainSubstrByWidth(remaining, maxWidth);
			int breakAt = head.lastIndexOf(' ');
			if (breakAt <= 0) {
				breakAt = Math.max(1, head.length());
			}
			g.text(font, remaining.substring(0, breakAt).strip(), x, cursorY,
				Theme.withAlpha(color, opacity), false);
			remaining = remaining.substring(breakAt).strip();
			cursorY += 10;
		}
		return cursorY;
	}

	/** Section title with a hairline rule running to the right edge. Returns the next free y. */
	public static int sectionHeader(GuiGraphicsExtractor g, Font font, String text, int x, int y, int width,
									int color, float opacity) {
		g.text(font, text, x, y, Theme.withAlpha(color, opacity), false);
		int ruleX = x + font.width(text) + 6;
		int ruleEnd = x + width;
		if (ruleEnd > ruleX) {
			g.fill(ruleX, y + 4, ruleEnd, y + 5, Theme.withAlpha(Theme.BORDER_SOFT, opacity * 0.7F));
		}
		return y + 13;
	}

	public static void divider(GuiGraphicsExtractor g, int x, int y, int width, float opacity) {
		g.fill(x, y, x + width, y + 1, Theme.withAlpha(Theme.BORDER_SOFT, opacity * 0.6F));
	}

	/**
	 * A large number with a caption underneath, used for the figures that should be readable at a
	 * glance (frame-rate delta, latency delta, capability score).
	 */
	public static void statBlock(GuiGraphicsExtractor g, Font font, String value, String caption,
								 int x, int y, int width, int valueColor, float opacity) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(1.6F);
		g.text(font, value, 0, 0, Theme.withAlpha(valueColor, opacity), true);
		g.pose().popMatrix();
		clippedText(g, font, caption, x, y + 15, width, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
	}

	/** Small rounded pill, used for release channels and conflict severities. */
	public static int badge(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, float opacity) {
		int width = font.width(text) + 8;
		roundedRect(g, x, y - 1, width, 11, 3, Theme.argb(Math.round(64 * opacity), color & 0xFFFFFF));
		outline(g, x, y - 1, width, 11, Theme.argb(Math.round(120 * opacity), color & 0xFFFFFF));
		g.text(font, text, x + 4, y + 1, Theme.withAlpha(color, opacity), false);
		return width;
	}

	/** 0..1 triangle wave. */
	public static float wave(long now, long period) {
		return 0.5F + 0.5F * Mth.sin((now % period) / (float) period * Mth.TWO_PI);
	}

	/** 0..1 sawtooth. */
	public static float cycle(long now, long period) {
		return Math.floorMod(now, period) / (float) period;
	}

	/** Smoothstep easing, used for the panel entrance animation. */
	public static float easeOut(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - (1.0F - c) * (1.0F - c) * (1.0F - c);
	}
}

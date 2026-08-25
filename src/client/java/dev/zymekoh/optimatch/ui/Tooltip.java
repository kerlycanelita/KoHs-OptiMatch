package dev.zymekoh.optimatch.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Hover tooltips drawn inside the selector's own canvas.
 *
 * <p>Minecraft's {@code setTooltipForNextFrame} stores screen coordinates and renders after the
 * screen's own drawing, which would land in the wrong place while a {@code pose()} scale is active.
 * Collecting the request during render and painting it last, still inside the transform, keeps the
 * tooltip glued to the thing being hovered at any {@link UiScale}.
 */
public final class Tooltip {
	private static final int MAX_WIDTH = 220;

	private static String pendingTitle;
	private static String pendingBody;
	private static int pendingX;
	private static int pendingY;

	private Tooltip() {
	}

	/** Requests a tooltip for this frame. The last caller wins, which matches hover semantics. */
	public static void request(String title, String body, int x, int y) {
		pendingTitle = title;
		pendingBody = body;
		pendingX = x;
		pendingY = y;
	}

	/** Convenience for a hover test: only requests when the cursor is inside the rectangle. */
	public static void requestIfHovered(int mouseX, int mouseY, int x, int y, int width, int height,
										String title, String body) {
		if (Draw.inside(mouseX, mouseY, x, y, width, height)) {
			request(title, body, mouseX, mouseY);
		}
	}

	public static void clear() {
		pendingTitle = null;
		pendingBody = null;
	}

	/** Paints whatever was requested this frame. Call last, inside the canvas transform. */
	public static void renderPending(GuiGraphicsExtractor graphics, Font font, int canvasWidth, int canvasHeight,
									 float opacity) {
		if (pendingTitle == null || opacity <= 0.05F) {
			return;
		}

		List<String> lines = new ArrayList<>();
		int width = Math.min(MAX_WIDTH, Math.max(font.width(pendingTitle), 40));

		if (pendingBody != null && !pendingBody.isBlank()) {
			String remaining = pendingBody.replaceAll("\\s+", " ").strip();
			int guard = remaining.length() + 2;
			while (!remaining.isEmpty() && lines.size() < 5 && guard-- > 0) {
				if (font.width(remaining) <= MAX_WIDTH) {
					lines.add(remaining);
					break;
				}
				String head = font.plainSubstrByWidth(remaining, MAX_WIDTH);
				int breakAt = head.lastIndexOf(' ');
				if (breakAt <= 0) {
					breakAt = Math.max(1, head.length());
				}
				lines.add(remaining.substring(0, breakAt).strip());
				remaining = remaining.substring(breakAt).strip();
			}
			for (String line : lines) {
				width = Math.max(width, font.width(line));
			}
		}
		width = Math.min(MAX_WIDTH, width) + 10;

		int height = 8 + 11 + (lines.isEmpty() ? 0 : lines.size() * 10);

		// Prefer below-right of the cursor, but flip rather than run off the canvas.
		int x = pendingX + 10;
		int y = pendingY + 10;
		if (x + width > canvasWidth - 4) {
			x = Math.max(4, pendingX - width - 6);
		}
		if (y + height > canvasHeight - 4) {
			y = Math.max(4, pendingY - height - 6);
		}

		Draw.roundedRect(graphics, x - 1, y + 1, width + 2, height + 2, 5, Theme.argb(Math.round(120 * opacity), 0));
		Draw.roundedRect(graphics, x, y, width, height, 4, Theme.withAlpha(Theme.ACCENT, opacity));
		Draw.roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 3,
			Theme.argb(Math.round(248 * opacity), 0x140A22));

		graphics.text(font, pendingTitle, x + 5, y + 5, Theme.withAlpha(Theme.TEXT, opacity), false);
		int lineY = y + 17;
		for (String line : lines) {
			graphics.text(font, line, x + 5, lineY, Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			lineY += 10;
		}

		clear();
	}
}

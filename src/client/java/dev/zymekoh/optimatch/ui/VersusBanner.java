package dev.zymekoh.optimatch.ui;

import dev.zymekoh.optimatch.scan.Conflict;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The head-to-head card for a conflict: one mod's icon, a fighting-game style VS, and the other's.
 *
 * <p>Only drawn when exactly two mods genuinely collide over the same method. A method that three
 * mods merely {@code @Inject} into is not a fight and gets the plain list instead — dressing that up
 * as a duel would cry wolf.
 */
public final class VersusBanner {
	private VersusBanner() {
	}

	/**
	 * @param left  the contender drawn on the left
	 * @param right the contender drawn on the right
	 */
	public static void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
							  Conflict.Contender left, Conflict.Contender right, long now, float opacity) {
		int centerX = x + width / 2;
		int centerY = y + height / 2;

		// Two slate halves meeting at an angle. Muted on purpose: this panel reports a technical
		// finding, and arcade pinks made it read as a joke rather than as information.
		int slant = Math.max(6, height / 3);
		drawSlantedHalf(graphics, x, y, width / 2, height, slant, true,
			Theme.argb(Math.round(58 * opacity), 0x3A3050));
		drawSlantedHalf(graphics, centerX, y, width / 2, height, slant, false,
			Theme.argb(Math.round(58 * opacity), 0x2A3448));

		// A single seam down the join, drawn instead of speed lines: quieter, and it points at the
		// one thing that matters here — the place the two mods meet.
		int seamAlpha = Math.round(50 * opacity);
		for (int row = 0; row < height; row++) {
			float t = row / (float) Math.max(1, height - 1);
			int seamX = centerX + Math.round((t - 0.5F) * slant);
			graphics.fill(seamX, y + row, seamX + 1, y + row + 1, Theme.argb(seamAlpha, Theme.ACCENT_RGB));
		}

		int iconSize = Math.min(30, height - 16);
		int iconY = centerY - iconSize / 2;

		// The VS owns the middle; each side gets a column that stops short of it, so the text can
		// never run under the mark however wide the dialog gets.
		int versusHalf = Math.max(24, font.width("VS") * 2);
		int leftStart = x + 10;
		int leftEnd = centerX - versusHalf;
		int rightStart = centerX + versusHalf;
		int rightEnd = x + width - 10;

		drawFighter(graphics, font, left, leftStart, leftEnd, iconY, iconSize, true, opacity);
		drawFighter(graphics, font, right, rightStart, rightEnd, iconY, iconSize, false, opacity);

		drawVersus(graphics, font, centerX, centerY, now, opacity);
	}

	/**
	 * One side of the card, laid out inside its own column so the two can never collide.
	 *
	 * <p>Both portraits sit on the outer edges and their text runs inward toward the VS, which is the
	 * arrangement a fighting-game select screen uses.
	 *
	 * @param columnStart left edge of this side's column
	 * @param columnEnd   right edge of this side's column
	 */
	private static void drawFighter(GuiGraphicsExtractor graphics, Font font, Conflict.Contender contender,
									int columnStart, int columnEnd, int iconY, int iconSize, boolean leftSide,
									float opacity) {
		int columnWidth = Math.max(0, columnEnd - columnStart);
		if (columnWidth < iconSize + 12) {
			// Too narrow for text: show the portrait alone rather than a mangled overlap.
			int iconOnly = leftSide ? columnStart : columnEnd - iconSize;
			ModIcons.draw(graphics, font, contender.modId(), contender.displayName(),
				iconOnly, iconY, iconSize, Theme.ACCENT, opacity);
			return;
		}

		String name = contender.displayName();
		String kind = contender.kind().label();
		String priority = "prio " + contender.priority();

		// Icon and text travel together as one block, so the portrait never ends up stranded at the
		// far edge with its own label sitting way over by the VS.
		int available = columnWidth - iconSize - 6;
		int natural = Math.max(font.width(name), Math.max(font.width(kind), font.width(priority)));
		int textWidth = Math.max(0, Math.min(natural, available));
		if (textWidth <= 8) {
			// No room for a label: centre the portrait in its column instead.
			int iconOnly = columnStart + (columnWidth - iconSize) / 2;
			ModIcons.draw(graphics, font, contender.modId(), name, iconOnly, iconY, iconSize,
				Theme.ACCENT, opacity);
			return;
		}

		int blockWidth = iconSize + 6 + textWidth;
		// Left block hugs the outer edge; right block hugs its own outer edge too, so both sit
		// against the frame and read as two corners rather than drifting toward the middle.
		int blockX = leftSide ? columnStart : columnEnd - blockWidth;

		int iconX = leftSide ? blockX : blockX + textWidth + 6;
		int textX = leftSide ? blockX + iconSize + 6 : blockX;

		ModIcons.draw(graphics, font, contender.modId(), name, iconX, iconY, iconSize, Theme.ACCENT, opacity);
		Draw.outline(graphics, iconX - 1, iconY - 1, iconSize + 2, iconSize + 2,
			Theme.withAlpha(leftSide ? 0xFFFF9EB5 : 0xFF9EC6FF, opacity));

		Draw.clippedText(graphics, font, name, textX, iconY + 3, textWidth,
			Theme.withAlpha(Theme.TEXT, opacity), false);
		Draw.clippedText(graphics, font, kind, textX, iconY + 13, textWidth,
			Theme.withAlpha(Theme.WARN, opacity), false);
		Draw.clippedText(graphics, font, priority, textX, iconY + 22, textWidth,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
	}

	/**
	 * The VS mark: a steady badge rather than a flashing one.
	 *
	 * <p>The pulse is slow and shallow. A conflict report is read, not watched, and a mark that
	 * throbs pulls the eye away from the text that carries the actual finding.
	 */
	private static void drawVersus(GuiGraphicsExtractor graphics, Font font, int centerX, int centerY,
								   long now, float opacity) {
		float pulse = Draw.wave(now, 2600L);

		String vs = "VS";
		int textWidth = font.width(vs);
		int badgeWidth = textWidth + 14;
		int badgeHeight = 18;
		int badgeX = centerX - badgeWidth / 2;
		int badgeY = centerY - badgeHeight / 2;

		Draw.roundedRect(graphics, badgeX, badgeY, badgeWidth, badgeHeight, 3,
			Theme.argb(Math.round(220 * opacity), 0x140C22));
		Draw.outline(graphics, badgeX, badgeY, badgeWidth, badgeHeight,
			Theme.argb(Math.round((120 + pulse * 60) * opacity), Theme.ACCENT_RGB));

		graphics.text(font, vs, centerX - textWidth / 2, centerY - 4,
			Theme.argb(Math.round(255 * opacity), Theme.brighten(Theme.ACCENT_RGB, 0.55F)), false);
	}

	/** A rectangle with one slanted edge, so the two halves meet at an angle. */
	private static void drawSlantedHalf(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
										int slant, boolean leftSide, int color) {
		for (int row = 0; row < height; row++) {
			float t = row / (float) Math.max(1, height - 1);
			int offset = Math.round((leftSide ? t : 1.0F - t) * slant);
			int rowX = leftSide ? x : x + offset;
			int rowWidth = leftSide ? width + offset - slant : width - offset;
			if (rowWidth > 0) {
				graphics.fill(rowX, y + row, rowX + rowWidth, y + row + 1, color);
			}
		}
	}

	/** Compact inline version for a list row: two icons with a small VS between them. */
	public static void renderInline(GuiGraphicsExtractor graphics, Font font, int x, int y, int size,
									Conflict.Contender left, Conflict.Contender right, long now, float opacity) {
		ModIcons.draw(graphics, font, left.modId(), left.displayName(), x, y, size, Theme.ACCENT, opacity);

		int gap = 14;
		int centerX = x + size + gap / 2;
		String vs = "VS";
		int textWidth = font.width(vs);
		graphics.text(font, vs, centerX - textWidth / 2, y + (size - 8) / 2,
			Theme.argb(Math.round(210 * opacity), Theme.brighten(Theme.ACCENT_RGB, 0.5F)));

		ModIcons.draw(graphics, font, right.modId(), right.displayName(),
			x + size + gap, y, size, Theme.ACCENT, opacity);
	}

	/** Total width {@link #renderInline} needs. */
	public static int inlineWidth(int size) {
		return size * 2 + 14;
	}

	/** Clamped helper kept for callers that need the pulse elsewhere. */
	public static float pulse(long now) {
		return Mth.clamp(Draw.wave(now, 900L), 0.0F, 1.0F);
	}
}

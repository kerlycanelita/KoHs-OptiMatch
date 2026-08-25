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

		// Angled arena backdrop: two tinted halves leaning into the middle.
		int slant = Math.max(6, height / 3);
		drawSlantedHalf(graphics, x, y, width / 2, height, slant, true,
			Theme.argb(Math.round(70 * opacity), 0x8B2E4A));
		drawSlantedHalf(graphics, centerX, y, width / 2, height, slant, false,
			Theme.argb(Math.round(70 * opacity), 0x2E4A8B));

		// Speed lines rushing toward the clash, which is what sells the motion.
		for (int i = 0; i < 5; i++) {
			float phase = Draw.cycle(now + i * 220L, 1500L);
			int lineY = y + 6 + i * Math.max(3, (height - 12) / 5);
			int reach = Math.round(phase * (width / 2.0F - 26));
			int alpha = Math.round((1.0F - phase) * 80 * opacity);
			graphics.fill(x + 4 + reach, lineY, x + 16 + reach, lineY + 1, Theme.argb(alpha, 0xFFD2E0));
			graphics.fill(x + width - 16 - reach, lineY, x + width - 4 - reach, lineY + 1,
				Theme.argb(alpha, 0xD2E0FF));
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

	/** The VS mark itself: pulsing, slightly rotated by a shear, with an impact flash behind it. */
	private static void drawVersus(GuiGraphicsExtractor graphics, Font font, int centerX, int centerY,
								   long now, float opacity) {
		float pulse = Draw.wave(now, 900L);
		int burst = 12 + Math.round(pulse * 4);

		// Impact star behind the letters.
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4 + pulse * 0.3;
			int dx = (int) Math.round(Math.cos(angle) * burst);
			int dy = (int) Math.round(Math.sin(angle) * burst);
			graphics.fill(centerX + dx - 1, centerY + dy - 1, centerX + dx + 1, centerY + dy + 1,
				Theme.argb(Math.round((90 + pulse * 90) * opacity), 0xFFE58A));
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(1.7F + pulse * 0.18F);
		graphics.pose().translate(-centerX, -centerY);

		String vs = "VS";
		int textWidth = font.width(vs);
		// Drawn twice: a dark offset copy gives the letters a hard edge at this scale.
		graphics.text(font, vs, centerX - textWidth / 2 + 1, centerY - 3,
			Theme.argb(Math.round(220 * opacity), 0x2A0A18), false);
		graphics.text(font, vs, centerX - textWidth / 2, centerY - 4,
			Theme.argb(Math.round(255 * opacity), 0xFFE58A), false);

		graphics.pose().popMatrix();
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
		float pulse = Draw.wave(now, 900L);
		String vs = "VS";
		int textWidth = font.width(vs);
		graphics.text(font, vs, centerX - textWidth / 2, y + (size - 8) / 2,
			Theme.argb(Math.round((190 + pulse * 65) * opacity), 0xFFE58A));

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

package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Circular progress marks for the startup sequence.
 *
 * <p>Drawn as points stepped around a circle rather than as a filled shape, which keeps the pixel
 * look of the rest of the interface and stays crisp at any {@link UiScale}.
 */
public final class Spinner {
	private Spinner() {
	}

	/**
	 * An indeterminate ring: a comet of dots chasing itself around the circle.
	 *
	 * @param radius distance from the centre to the dots
	 */
	public static void indeterminate(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius,
									 long now, float opacity) {
		int dots = 12;
		float head = Draw.cycle(now, 1100L) * dots;

		for (int index = 0; index < dots; index++) {
			// Distance behind the head, wrapped, so the trail fades evenly around the ring.
			float behind = Math.floorMod((int) head - index, dots) / (float) dots;
			float brightness = (1.0F - behind) * (1.0F - behind);
			int alpha = Math.round(30 + brightness * 225 * opacity);
			if (alpha <= 6) {
				continue;
			}

			double angle = index * (Math.PI * 2.0) / dots - Math.PI / 2.0;
			int x = centerX + (int) Math.round(Math.cos(angle) * radius);
			int y = centerY + (int) Math.round(Math.sin(angle) * radius);
			int size = brightness > 0.6F ? 3 : 2;

			int color = Theme.mix(Theme.ACCENT_RGB, 0xFFFFFF, brightness * 0.6F);
			graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size,
				Theme.argb(alpha, color));
		}
	}

	/**
	 * A determinate arc that fills clockwise from the top.
	 *
	 * @param progress 0..1
	 */
	public static void progress(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius,
								float progress, float opacity) {
		int steps = 48;
		int filled = Math.round(Mth.clamp(progress, 0.0F, 1.0F) * steps);

		for (int index = 0; index < steps; index++) {
			double angle = index * (Math.PI * 2.0) / steps - Math.PI / 2.0;
			int x = centerX + (int) Math.round(Math.cos(angle) * radius);
			int y = centerY + (int) Math.round(Math.sin(angle) * radius);

			boolean on = index < filled;
			int color = on ? Theme.ACCENT_BRIGHT : Theme.BORDER_SOFT;
			int alpha = Math.round((on ? 235 : 70) * opacity);
			int size = on ? 2 : 1;
			graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size,
				Theme.argb(alpha, color & 0xFFFFFF));
		}
	}

	/**
	 * A soft pulsing halo, used behind the mascot so the loading screen has some depth.
	 */
	public static void halo(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius,
							long now, float opacity) {
		float pulse = Draw.wave(now, 2200L);
		int rings = 3;
		for (int ring = 0; ring < rings; ring++) {
			int ringRadius = radius + ring * 4 + Math.round(pulse * 3);
			int alpha = Math.round((26 - ring * 7) * opacity);
			if (alpha <= 2) {
				continue;
			}
			circleOutline(graphics, centerX, centerY, ringRadius, Theme.argb(alpha, Theme.ACCENT_RGB));
		}
	}

	private static void circleOutline(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
		int steps = Math.max(16, radius * 3);
		for (int index = 0; index < steps; index++) {
			double angle = index * (Math.PI * 2.0) / steps;
			int x = centerX + (int) Math.round(Math.cos(angle) * radius);
			int y = centerY + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}
}

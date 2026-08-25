package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The little hooded character that shows up when something fails to load.
 *
 * <p>An error screen is where a tool feels most broken, so this one gets the most care: the mascot
 * paces left and right with a real walk cycle — legs alternating, arms counter-swinging, a bob on
 * each step and a turn at the end of the walk — instead of sitting still.
 *
 * <p>It is drawn as pixel art from rectangles rather than a texture, so it scales cleanly to any
 * {@link UiScale} and needs no asset. Every sprite coordinate is written facing right and mirrored
 * through {@link #px} when walking the other way.
 */
public final class Mascot {
	// Palette taken from the mod's own skin: purple hood, dark fringe, white front.
	private static final int HOOD = 0x6B3FA0;
	private static final int HOOD_LIGHT = 0x8B5FD0;
	private static final int HOOD_DARK = 0x4A2A73;
	private static final int EAR_INNER = 0xE0B8E8;
	private static final int HAIR = 0x241B3A;
	private static final int SKIN = 0x8B5E4A;
	private static final int EYE = 0x1A1030;
	private static final int WHITE = 0xF2F0F5;

	/** One walk cycle, in milliseconds. */
	private static final long STEP_MILLIS = 620L;
	/** How long a full pace across and back takes. */
	private static final long PACE_MILLIS = 5200L;

	private Mascot() {
	}

	/**
	 * Draws the mascot pacing inside a horizontal band.
	 *
	 * @param centerY  vertical baseline: the character's feet rest here
	 * @param scale    pixel size; 2 gives a 32x48 character
	 */
	public static void renderWalking(GuiGraphicsExtractor graphics, int x, int centerY, int width,
									 int scale, long now, float opacity) {
		if (opacity <= 0.02F || scale < 1) {
			return;
		}

		int bodyWidth = 16 * scale;
		int travel = Math.max(0, width - bodyWidth);

		// Triangle wave: walk to the far side, turn, walk back.
		float cycle = (now % PACE_MILLIS) / (float) PACE_MILLIS;
		boolean facingRight = cycle < 0.5F;
		float across = facingRight ? cycle * 2.0F : (1.0F - cycle) * 2.0F;
		int footX = x + Math.round(across * travel);

		// Step phase drives the legs, and a half-frequency bob rides on top of it.
		float step = (now % STEP_MILLIS) / (float) STEP_MILLIS * Mth.TWO_PI;
		int bob = Math.round(Math.abs(Mth.sin(step)) * scale * 0.5F);

		drawCharacter(graphics, footX, centerY - bob, scale, facingRight, step, now, opacity);
	}

	/** Static pose, for places that want the character without the pacing. */
	public static void renderIdle(GuiGraphicsExtractor graphics, int x, int feetY, int scale, long now, float opacity) {
		drawCharacter(graphics, x, feetY, scale, true, 0.0F, now, opacity);
	}

	private static void drawCharacter(GuiGraphicsExtractor graphics, int originX, int feetY, int scale,
									  boolean facingRight, float step, long now, float opacity) {
		int top = feetY - 24 * scale;

		// Legs swing in opposition; arms mirror them so the gait reads as walking.
		int frontLeg = Math.round(Mth.sin(step) * scale * 1.6F);
		int backLeg = -frontLeg;
		int frontArm = -frontLeg;

		// ---- legs (drawn first so the body overlaps them) ----
		rect(graphics, originX, top, scale, facingRight, 4, 17, 3, 5, HOOD, opacity, backLeg, 0);
		rect(graphics, originX, top, scale, facingRight, 4, 22, 3, 2, WHITE, opacity, backLeg, 0);
		rect(graphics, originX, top, scale, facingRight, 9, 17, 3, 5, HOOD_LIGHT, opacity, frontLeg, 0);
		rect(graphics, originX, top, scale, facingRight, 9, 22, 3, 2, WHITE, opacity, frontLeg, 0);

		// ---- torso: purple hoodie with the white front panel ----
		rect(graphics, originX, top, scale, facingRight, 3, 10, 10, 8, HOOD, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 6, 11, 4, 7, WHITE, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 3, 10, 10, 1, HOOD_DARK, opacity, 0, 0);

		// ---- arms ----
		rect(graphics, originX, top, scale, facingRight, 1, 10, 2, 7, HOOD_LIGHT, opacity, 0, frontArm);
		rect(graphics, originX, top, scale, facingRight, 1, 16, 2, 2, SKIN, opacity, 0, frontArm);
		rect(graphics, originX, top, scale, facingRight, 13, 10, 2, 7, HOOD, opacity, 0, -frontArm);
		rect(graphics, originX, top, scale, facingRight, 13, 16, 2, 2, SKIN, opacity, 0, -frontArm);

		// ---- head: hood, ears, fringe, face ----
		rect(graphics, originX, top, scale, facingRight, 2, 1, 12, 9, HOOD, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 2, 1, 12, 1, HOOD_LIGHT, opacity, 0, 0);

		// Cat ears.
		rect(graphics, originX, top, scale, facingRight, 2, -1, 3, 2, HOOD, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 3, 0, 1, 1, EAR_INNER, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 11, -1, 3, 2, HOOD, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 12, 0, 1, 1, EAR_INNER, opacity, 0, 0);

		// Fringe and face opening.
		rect(graphics, originX, top, scale, facingRight, 3, 2, 10, 3, HAIR, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 4, 5, 8, 4, SKIN, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 3, 4, 2, 4, HAIR, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 11, 4, 2, 4, HAIR, opacity, 0, 0);

		// A slow blink keeps it feeling alive without being distracting.
		boolean blinking = now % 3400L < 130L;
		int eyeHeight = blinking ? 1 : 2;
		rect(graphics, originX, top, scale, facingRight, 5, 6, 2, eyeHeight, EYE, opacity, 0, 0);
		rect(graphics, originX, top, scale, facingRight, 9, 6, 2, eyeHeight, EYE, opacity, 0, 0);

		// Soft contact shadow, so it reads as standing on something.
		int shadowWidth = 12 * scale;
		int shadowX = originX + 2 * scale;
		graphics.fill(shadowX, feetY + scale, shadowX + shadowWidth, feetY + scale + Math.max(1, scale / 2),
			Theme.argb(Math.round(60 * opacity), 0x000000));
	}

	/**
	 * Draws one sprite rectangle in character space.
	 *
	 * @param spriteX left edge in the 16-wide sprite grid, always written facing right
	 * @param offsetX extra horizontal shift in scaled pixels, for swinging limbs
	 */
	private static void rect(GuiGraphicsExtractor graphics, int originX, int originY, int scale, boolean facingRight,
							 int spriteX, int spriteY, int spriteWidth, int spriteHeight, int color, float opacity,
							 int offsetX, int armOffset) {
		int shift = offsetX + armOffset;
		int localX = facingRight ? spriteX : 16 - spriteX - spriteWidth;
		int drawX = originX + localX * scale + (facingRight ? shift : -shift);
		int drawY = originY + spriteY * scale;

		graphics.fill(drawX, drawY, drawX + spriteWidth * scale, drawY + spriteHeight * scale,
			Theme.argb(Math.round(255 * opacity), color));
	}

	/**
	 * The full "something went wrong" panel: the pacing mascot over a message.
	 *
	 * <p>Used wherever a network call fails, so an error looks like the tool caring rather than the
	 * tool breaking.
	 */
	public static void renderErrorState(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
										String title, String detail, long now, float opacity) {
		int scale = height >= 110 ? 2 : 1;
		int centerX = x + width / 2;

		// The mascot paces along a band in the upper half of the free space.
		int walkWidth = Math.min(width - 20, 150);
		int walkX = centerX - walkWidth / 2;
		int feetY = y + height / 2 - 6;
		renderWalking(graphics, walkX, feetY, walkWidth, scale, now, opacity);

		// Ground line it walks along.
		graphics.fill(walkX, feetY + scale + 2, walkX + walkWidth, feetY + scale + 3,
			Theme.argb(Math.round(70 * opacity), Theme.ACCENT_RGB));

		int textY = feetY + 14;
		graphics.centeredText(font, title, centerX, textY, Theme.withAlpha(Theme.TEXT_MUTED, opacity));
		if (detail != null && !detail.isBlank()) {
			Draw.wrappedText(graphics, font, detail, x + 10, textY + 12, width - 20, 3, Theme.TEXT_DIM, opacity);
		}
	}
}

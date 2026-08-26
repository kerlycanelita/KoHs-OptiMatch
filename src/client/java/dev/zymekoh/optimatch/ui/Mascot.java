package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The little hooded character that shows up when something fails to load.
 *
 * <p>An error screen is where a tool feels most broken, so this one gets the most care. The walk is
 * a real cycle rather than a horizontal slide: the forward leg lifts as it swings, the body bobs on
 * each step, the arms counter-swing, and the character leans into the direction of travel and pauses
 * to turn at each end.
 *
 * <p>Drawn from rectangles rather than a texture, so it scales cleanly to any {@link UiScale} and
 * needs no asset.
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

	/** One full stride, in milliseconds. */
	private static final long STEP_MILLIS = 700L;
	/** How long a pace across and back takes, turns included. */
	private static final long PACE_MILLIS = 6000L;
	/** Share of each half-lap spent standing still at the far end, turning around. */
	private static final float TURN_PAUSE = 0.12F;

	private Mascot() {
	}

	/**
	 * Draws the mascot pacing inside a horizontal band.
	 *
	 * @param groundY where the feet rest; the shadow stays on this line even while the body bobs
	 * @param scale   pixel size; 2 gives a 32x48 character
	 */
	public static void renderWalking(GuiGraphicsExtractor graphics, int x, int groundY, int width,
									 int scale, long now, float opacity) {
		if (opacity <= 0.02F || scale < 1) {
			return;
		}

		int bodyWidth = 16 * scale;
		int travel = Math.max(0, width - bodyWidth);

		float cycle = (now % PACE_MILLIS) / (float) PACE_MILLIS;
		boolean facingRight = cycle < 0.5F;

		// Position within this half-lap, with a pause held at the end for the turn.
		float half = facingRight ? cycle * 2.0F : (cycle - 0.5F) * 2.0F;
		float moving = Mth.clamp(half / (1.0F - TURN_PAUSE), 0.0F, 1.0F);
		boolean turning = half > 1.0F - TURN_PAUSE;

		float across = facingRight ? moving : 1.0F - moving;
		int footX = x + Math.round(across * travel);

		// The gait only runs while actually moving, so the turn reads as a genuine stop.
		float step = turning ? 0.0F : (now % STEP_MILLIS) / (float) STEP_MILLIS * Mth.TWO_PI;
		// Two bobs per stride: one per footfall.
		int bob = turning ? 0 : Math.round(Math.abs(Mth.sin(step)) * scale * 0.6F);

		drawCharacter(graphics, footX, groundY, bob, scale, facingRight, step, turning, now, opacity);
	}

	/** Static pose, for places that want the character without the pacing. */
	public static void renderIdle(GuiGraphicsExtractor graphics, int x, int groundY, int scale, long now, float opacity) {
		// A slow breath keeps an idle pose from looking like a frozen frame.
		int breath = Math.round((0.5F + 0.5F * Mth.sin(now / 900.0F)) * scale * 0.4F);
		drawCharacter(graphics, x, groundY, breath, scale, true, 0.0F, true, now, opacity);
	}

	private static void drawCharacter(GuiGraphicsExtractor graphics, int originX, int groundY, int bob, int scale,
									  boolean facingRight, float step, boolean turning, long now, float opacity) {
		// The shadow belongs to the ground, not to the body: it must not travel with the bob. It only
		// tightens as the character rises, which is what makes the lift read as a lift.
		int shadowInset = bob;
		int shadowWidth = Math.max(4 * scale, 12 * scale - shadowInset * 2);
		int shadowX = originX + 2 * scale + shadowInset;
		int shadowAlpha = Math.round((70 - bob * 8) * opacity);
		if (shadowAlpha > 4) {
			graphics.fill(shadowX, groundY + scale, shadowX + shadowWidth,
				groundY + scale + Math.max(1, scale / 2), Theme.argb(shadowAlpha, 0x000000));
		}

		int feetY = groundY - bob;
		int top = feetY - 24 * scale;

		// Legs swing in opposition, and the one swinging forward lifts off the ground.
		float swing = Mth.sin(step);
		int frontLeg = Math.round(swing * scale * 1.8F);
		int backLeg = -frontLeg;
		int frontLift = Math.max(0, Math.round(swing * scale * 1.2F));
		int backLift = Math.max(0, Math.round(-swing * scale * 1.2F));

		// A slight lean into the direction of travel, dropped while turning.
		int lean = turning ? 0 : Math.round(scale * 0.6F);

		// ---- legs (drawn first so the body overlaps them) ----
		leg(graphics, originX, top, scale, facingRight, 4, backLeg, backLift, HOOD, opacity);
		leg(graphics, originX, top, scale, facingRight, 9, frontLeg, frontLift, HOOD_LIGHT, opacity);

		// ---- torso ----
		rect(graphics, originX, top, scale, facingRight, 3, 10, 10, 8, HOOD, opacity, lean);
		rect(graphics, originX, top, scale, facingRight, 6, 11, 4, 7, WHITE, opacity, lean);
		rect(graphics, originX, top, scale, facingRight, 3, 10, 10, 1, HOOD_DARK, opacity, lean);

		// ---- arms, counter-swinging against the legs ----
		int frontArm = -frontLeg;
		rect(graphics, originX, top, scale, facingRight, 1, 10, 2, 7, HOOD_LIGHT, opacity, lean + frontArm);
		rect(graphics, originX, top, scale, facingRight, 1, 16, 2, 2, SKIN, opacity, lean + frontArm);
		rect(graphics, originX, top, scale, facingRight, 13, 10, 2, 7, HOOD, opacity, lean - frontArm);
		rect(graphics, originX, top, scale, facingRight, 13, 16, 2, 2, SKIN, opacity, lean - frontArm);

		// ---- head: leans a touch further than the body ----
		int headLean = lean + (turning ? 0 : Math.round(scale * 0.4F));
		rect(graphics, originX, top, scale, facingRight, 2, 1, 12, 9, HOOD, opacity, headLean);
		rect(graphics, originX, top, scale, facingRight, 2, 1, 12, 1, HOOD_LIGHT, opacity, headLean);

		// Cat ears, with a small delay so they lag behind the head.
		int earLag = turning ? 0 : Math.round(Mth.sin(step - 0.6F) * scale * 0.5F);
		rect(graphics, originX, top, scale, facingRight, 2, -1, 3, 2, HOOD, opacity, headLean + earLag);
		rect(graphics, originX, top, scale, facingRight, 3, 0, 1, 1, EAR_INNER, opacity, headLean + earLag);
		rect(graphics, originX, top, scale, facingRight, 11, -1, 3, 2, HOOD, opacity, headLean + earLag);
		rect(graphics, originX, top, scale, facingRight, 12, 0, 1, 1, EAR_INNER, opacity, headLean + earLag);

		// Fringe and face opening.
		rect(graphics, originX, top, scale, facingRight, 3, 2, 10, 3, HAIR, opacity, headLean);
		rect(graphics, originX, top, scale, facingRight, 4, 5, 8, 4, SKIN, opacity, headLean);
		rect(graphics, originX, top, scale, facingRight, 3, 4, 2, 4, HAIR, opacity, headLean);
		rect(graphics, originX, top, scale, facingRight, 11, 4, 2, 4, HAIR, opacity, headLean);

		// A slow blink, plus a longer one while standing still at the turn.
		boolean blinking = now % 3400L < 130L || turning && now % 1400L < 200L;
		int eyeHeight = blinking ? 1 : 2;
		rect(graphics, originX, top, scale, facingRight, 5, 6, 2, eyeHeight, EYE, opacity, headLean);
		rect(graphics, originX, top, scale, facingRight, 9, 6, 2, eyeHeight, EYE, opacity, headLean);
	}

	/** One leg: swings horizontally and lifts vertically, which is what separates walking from sliding. */
	private static void leg(GuiGraphicsExtractor graphics, int originX, int originY, int scale, boolean facingRight,
							int spriteX, int swing, int lift, int color, float opacity) {
		int height = Math.max(1, 5 - Math.min(2, lift));
		rect(graphics, originX, originY - lift, scale, facingRight, spriteX, 17, 3, height, color, opacity, swing);
		rect(graphics, originX, originY - lift, scale, facingRight, spriteX, 17 + height, 3, 2, WHITE, opacity, swing);
	}

	/**
	 * Draws one sprite rectangle in character space.
	 *
	 * @param spriteX left edge in the 16-wide sprite grid, always written facing right
	 * @param offsetX horizontal shift in scaled pixels, mirrored along with the sprite
	 */
	private static void rect(GuiGraphicsExtractor graphics, int originX, int originY, int scale, boolean facingRight,
							 int spriteX, int spriteY, int spriteWidth, int spriteHeight, int color, float opacity,
							 int offsetX) {
		int localX = facingRight ? spriteX : 16 - spriteX - spriteWidth;
		int drawX = originX + localX * scale + (facingRight ? offsetX : -offsetX);
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

		int walkWidth = Math.min(width - 20, 150);
		int walkX = centerX - walkWidth / 2;
		int groundY = y + height / 2 - 6;

		// Ground line first, so the character walks on top of it.
		graphics.fill(walkX, groundY + scale + 2, walkX + walkWidth, groundY + scale + 3,
			Theme.argb(Math.round(70 * opacity), Theme.ACCENT_RGB));

		renderWalking(graphics, walkX, groundY, walkWidth, scale, now, opacity);

		int textY = groundY + 14;
		graphics.centeredText(font, title, centerX, textY, Theme.withAlpha(Theme.TEXT_MUTED, opacity));
		if (detail != null && !detail.isBlank()) {
			Draw.wrappedText(graphics, font, detail, x + 10, textY + 12, width - 20, 3, Theme.TEXT_DIM, opacity);
		}
	}
}

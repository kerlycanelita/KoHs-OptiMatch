package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Dense field of drifting background motes. Particles are generated once from a fixed seed and
 * animated purely as a function of time, so rendering allocates nothing per frame.
 */
public final class ParticleField {
	private static final int[] PALETTE = {0xE8C3FF, 0xC778FF, 0x8F42E8, 0xF5DEFF, 0x6C29B8};

	private final Mote[] motes;

	public ParticleField(int count) {
		this.motes = new Mote[Math.max(1, count)];
		long seed = 0x51ED270B;
		for (int i = 0; i < this.motes.length; i++) {
			seed = next(seed);
			float homeX = frac(seed);
			seed = next(seed);
			float phase = frac(seed);
			seed = next(seed);
			// Depth drives size, speed, drift and brightness together so the field reads as 3D.
			float depth = 0.25F + 0.75F * frac(seed);
			seed = next(seed);
			float swayAmount = 4.0F + 26.0F * frac(seed);
			seed = next(seed);
			float swaySpeed = 0.35F + 1.15F * frac(seed);
			seed = next(seed);
			int color = PALETTE[(int) (frac(seed) * PALETTE.length) % PALETTE.length];
			seed = next(seed);
			float twinkle = frac(seed) * Mth.TWO_PI;

			int size = depth > 0.82F ? 3 : depth > 0.55F ? 2 : 1;
			this.motes[i] = new Mote(homeX, phase, depth, swayAmount, swaySpeed, color, twinkle, size);
		}
	}

	/** Particle count that keeps the field dense but bounded on small windows. */
	public static ParticleField forViewport(int width, int height) {
		return new ParticleField(Mth.clamp(width * height / 950, 140, 420));
	}

	/**
	 * @param opacity global multiplier, used to fade the field in with the rest of the screen
	 */
	public void render(GuiGraphicsExtractor g, int width, int height, long now, float opacity) {
		if (opacity <= 0.01F || width <= 0 || height <= 0) {
			return;
		}
		float seconds = now / 1000.0F;
		int span = height + 40;

		for (Mote mote : this.motes) {
			// Rise speed scales with depth: near motes travel faster than far ones.
			float travel = (seconds * (7.0F + mote.depth * 22.0F) / span + mote.phase) % 1.0F;
			int y = height + 20 - Math.round(travel * span);
			if (y < -4 || y > height + 4) {
				continue;
			}

			float sway = Mth.sin(seconds * mote.swaySpeed + mote.twinkle) * mote.swayAmount * mote.depth;
			int x = Math.round(mote.homeX * width + sway);
			if (x < -4 || x > width + 4) {
				continue;
			}

			// Fade in at the bottom and out at the top so motes never pop.
			float edgeFade = Mth.clamp(Math.min(travel, 1.0F - travel) * 6.0F, 0.0F, 1.0F);
			float pulse = 0.55F + 0.45F * Mth.sin(seconds * 2.1F + mote.twinkle);
			int alpha = Math.round(190 * mote.depth * edgeFade * pulse * opacity);
			if (alpha <= 3) {
				continue;
			}

			int size = mote.size;
			if (size > 1) {
				// Soft halo around the larger motes.
				g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Theme.argb(alpha / 4, mote.color));
			}
			g.fill(x, y, x + size, y + size, Theme.argb(alpha, mote.color));
		}
	}

	private static long next(long seed) {
		// SplitMix64-style scrambling, deterministic across runs.
		long z = seed + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static float frac(long seed) {
		return (seed >>> 40) / (float) (1 << 24);
	}

	private record Mote(float homeX, float phase, float depth, float swayAmount, float swaySpeed, int color,
						float twinkle, int size) {
	}
}

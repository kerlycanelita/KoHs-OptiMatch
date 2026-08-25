package dev.zymekoh.optimatch.ui;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/** Easing curves and a frame-rate independent smoothed value, for the UI's motion. */
public final class Anim {
	private Anim() {
	}

	/** Decelerating cubic. The default for anything entering the screen. */
	public static float easeOut(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - (1.0F - c) * (1.0F - c) * (1.0F - c);
	}

	/** Accelerate then decelerate. Used for cross-fades where both ends should feel soft. */
	public static float easeInOut(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c < 0.5F ? 4.0F * c * c * c : 1.0F - (float) Math.pow(-2.0F * c + 2.0F, 3) / 2.0F;
	}

	/** Overshoots slightly before settling, which reads as "snappy" on panels appearing. */
	public static float easeOutBack(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		float overshoot = 1.70158F;
		float shifted = c - 1.0F;
		return 1.0F + (overshoot + 1.0F) * shifted * shifted * shifted + overshoot * shifted * shifted;
	}

	/** Progress of a one-shot animation that started at {@code startMillis}. */
	public static float progress(long startMillis, long durationMillis) {
		if (startMillis <= 0L) {
			return 1.0F;
		}
		return Mth.clamp((Util.getMillis() - startMillis) / (float) durationMillis, 0.0F, 1.0F);
	}

	/**
	 * A value that chases a target instead of snapping to it — used for scrolling, so the wheel
	 * glides rather than jumping.
	 */
	public static final class Smoothed {
		private float current;
		private float target;
		private long lastUpdate;

		/** Higher is snappier. 14 lands around 100 ms to settle. */
		private final float speed;

		public Smoothed(float speed) {
			this.speed = speed;
		}

		public void set(float value) {
			this.target = value;
		}

		public void snapTo(float value) {
			this.target = value;
			this.current = value;
		}

		public float target() {
			return this.target;
		}

		/** Advances toward the target using real elapsed time, so it looks the same at any frame rate. */
		public float value() {
			long now = Util.getMillis();
			if (this.lastUpdate == 0L) {
				this.lastUpdate = now;
				this.current = this.target;
				return this.current;
			}

			float deltaSeconds = Math.min(0.1F, (now - this.lastUpdate) / 1000.0F);
			this.lastUpdate = now;

			float factor = 1.0F - (float) Math.exp(-this.speed * deltaSeconds);
			this.current += (this.target - this.current) * factor;

			if (Math.abs(this.target - this.current) < 0.35F) {
				this.current = this.target;
			}
			return this.current;
		}

		public int intValue() {
			return Math.round(value());
		}
	}
}

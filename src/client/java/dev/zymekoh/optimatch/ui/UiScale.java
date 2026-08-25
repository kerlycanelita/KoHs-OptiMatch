package dev.zymekoh.optimatch.ui;

import net.minecraft.client.Minecraft;

/**
 * Lets the selector draw on its own canvas instead of inheriting the player's GUI scale.
 *
 * <p>The problem is not the GUI scale setting by itself, it is how little logical room is left: a
 * 854x480 window at scale 2, or a 1080p window at scale 4, both leave roughly {@code 430x240}
 * logical pixels — not enough for a five-tab console with a detail pane.
 *
 * <p>But a logical pixel is {@code guiScale} real screen pixels, so there is headroom to draw
 * smaller. The factor shrinks the drawing until the canvas is at least {@link #MIN_WIDTH} by
 * {@link #MIN_HEIGHT} virtual pixels, while never letting a virtual pixel fall below
 * {@link #MIN_REAL_PIXELS_PER_UNIT} real pixels — the density vanilla uses at GUI scale 1.
 *
 * <p>{@code GuiGraphicsExtractor.enableScissor} applies the current {@code pose()} to the rectangle
 * (it calls {@code ScreenRectangle.transformAxisAligned}), so clipping works with virtual
 * coordinates and needs no manual conversion.
 */
public final class UiScale {
	/** Canvas size the layout is designed around. */
	private static final int MIN_WIDTH = 640;
	private static final int MIN_HEIGHT = 360;

	/** Readability floor: one real screen pixel per virtual pixel, same as vanilla at scale 1. */
	private static final float MIN_REAL_PIXELS_PER_UNIT = 1.0F;

	private final float factor;
	private final int virtualWidth;
	private final int virtualHeight;

	private UiScale(float factor, int virtualWidth, int virtualHeight) {
		this.factor = factor;
		this.virtualWidth = virtualWidth;
		this.virtualHeight = virtualHeight;
	}

	/**
	 * @param screenWidth  {@code Screen.width}, already divided by the player's GUI scale
	 * @param screenHeight {@code Screen.height}
	 */
	public static UiScale of(int screenWidth, int screenHeight) {
		int guiScale = 1;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.getWindow() != null) {
			guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
		}

		// How much we would have to shrink to reach the design size, in each axis.
		float neededForWidth = screenWidth / (float) MIN_WIDTH;
		float neededForHeight = screenHeight / (float) MIN_HEIGHT;

		// Never magnify: a window that is already roomy keeps its native density.
		float desired = Math.min(1.0F, Math.min(neededForWidth, neededForHeight));

		// ...but never shrink past the point where text stops being legible.
		float floor = Math.min(1.0F, MIN_REAL_PIXELS_PER_UNIT / guiScale);
		float factor = Math.max(floor, desired);

		return new UiScale(
			factor,
			Math.max(1, Math.round(screenWidth / factor)),
			Math.max(1, Math.round(screenHeight / factor))
		);
	}

	/** Multiplier to hand to {@code pose().scale(...)} before drawing. */
	public float factor() {
		return this.factor;
	}

	/** Canvas width in virtual pixels; use this instead of {@code Screen.width}. */
	public int width() {
		return this.virtualWidth;
	}

	/** Canvas height in virtual pixels. */
	public int height() {
		return this.virtualHeight;
	}

	public boolean isScaled() {
		return this.factor < 0.999F;
	}

	/** Maps a real mouse coordinate onto the virtual canvas. */
	public double toVirtual(double screenCoordinate) {
		return screenCoordinate / this.factor;
	}

	/** Maps a real mouse coordinate onto the virtual canvas, rounded for render-time hit testing. */
	public int toVirtual(int screenCoordinate) {
		return Math.round(screenCoordinate / this.factor);
	}
}

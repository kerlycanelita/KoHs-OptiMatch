package dev.zymekoh.optimatch.ui;

/**
 * Dark purple palette for KoHs OptiMatch. Colours are ARGB unless the name ends in {@code _RGB}.
 */
public final class Theme {
	// Backdrop
	public static final int BG_TOP = 0xF2100722;
	public static final int BG_BOTTOM = 0xF6060310;
	public static final int GLOW_TOP = 0x8C4B1080;
	public static final int GLOW_FADE = 0x00220840;

	// Surfaces
	public static final int PANEL = 0xD91A0E2C;
	public static final int PANEL_RAISED = 0xE5241340;
	public static final int PANEL_HOVER = 0xE93A1C63;
	public static final int SHADOW = 0x66000000;

	// Accents
	public static final int ACCENT_RGB = 0x9B4DE0;
	public static final int ACCENT = 0xFF9B4DE0;
	public static final int ACCENT_BRIGHT = 0xFFD7A6FF;
	public static final int ACCENT_DIM = 0xAA6A2FA0;
	public static final int BORDER = 0xBF7C3FC4;
	public static final int BORDER_SOFT = 0x804A2670;

	// Window chrome. Desaturated on purpose: a saturated line plus an offset shadow reads as a
	// carved bevel, which made the frame look like a wooden plank instead of a panel edge.
	/** The window's outer edge: slate with only a trace of the accent hue. */
	public static final int FRAME = 0xA8433A5E;
	/** Single light source, from above: a faint inner highlight along the top edge. */
	public static final int FRAME_TOP = 0x24FFFFFF;
	/** The matching inner shade along the bottom, which grounds the panel. */
	public static final int FRAME_BOTTOM = 0x30000000;
	/** Soft halo drawn evenly around the frame, replacing the offset drop shadow. */
	public static final int FRAME_HALO = 0x38000000;

	// Text
	public static final int TEXT = 0xFFF6EEFF;
	public static final int TEXT_MUTED = 0xFFC3AAD6;
	public static final int TEXT_DIM = 0xFF8E76A3;

	// Semantic status colours (conflicts, ratings)
	public static final int GOOD = 0xFF5BE08A;
	public static final int WARN = 0xFFFFC65C;
	public static final int DANGER = 0xFFFF6B81;
	public static final int INFO = 0xFF6BC6FF;

	private Theme() {
	}

	public static int argb(int alpha, int rgb) {
		int a = alpha < 0 ? 0 : Math.min(alpha, 255);
		return a << 24 | rgb & 0xFFFFFF;
	}

	public static int withAlpha(int argb, float factor) {
		int alpha = Math.round(((argb >>> 24) & 255) * Math.max(0.0F, Math.min(factor, 1.0F)));
		return alpha << 24 | argb & 0xFFFFFF;
	}

	public static int mix(int firstRgb, int secondRgb, float amount) {
		float t = Math.max(0.0F, Math.min(amount, 1.0F));
		int r = Math.round(((firstRgb >> 16) & 255) * (1.0F - t) + ((secondRgb >> 16) & 255) * t);
		int g = Math.round(((firstRgb >> 8) & 255) * (1.0F - t) + ((secondRgb >> 8) & 255) * t);
		int b = Math.round((firstRgb & 255) * (1.0F - t) + (secondRgb & 255) * t);
		return r << 16 | g << 8 | b;
	}

	public static int brighten(int rgb, float amount) {
		return mix(rgb, 0xFFFFFF, amount);
	}

	public static int darken(int rgb, float amount) {
		return mix(rgb, 0x000000, amount);
	}
}

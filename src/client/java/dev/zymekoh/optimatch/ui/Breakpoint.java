package dev.zymekoh.optimatch.ui;

/**
 * Responsive breakpoints for the selector.
 *
 * <p>Minecraft's GUI scale means the usable width can be anywhere from ~320 (small window at scale 4)
 * to well over 1000 (large window at scale 1), so the layout has to adapt rather than assume a size.
 * Widths here are in GUI-scaled pixels, the same units {@code Screen.width} reports.
 */
public enum Breakpoint {
	/** Two columns do not fit: stack panels and drop secondary text. */
	COMPACT,
	/** Two columns fit, but space is tight. */
	REGULAR,
	/** Room for full labels, descriptions and wider detail panes. */
	WIDE;

	public static Breakpoint of(int width) {
		if (width < 430) {
			return COMPACT;
		}
		return width < 760 ? REGULAR : WIDE;
	}

	public boolean isAtLeast(Breakpoint other) {
		return this.ordinal() >= other.ordinal();
	}

	public boolean isCompact() {
		return this == COMPACT;
	}

	/** Picks one of three values for the current breakpoint. */
	public int pick(int compact, int regular, int wide) {
		return switch (this) {
			case COMPACT -> compact;
			case REGULAR -> regular;
			case WIDE -> wide;
		};
	}

	/** Same as {@link #pick(int, int, int)} for fractional values such as column ratios. */
	public float pick(float compact, float regular, float wide) {
		return switch (this) {
			case COMPACT -> compact;
			case REGULAR -> regular;
			case WIDE -> wide;
		};
	}
}

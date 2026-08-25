package dev.zymekoh.optimatch.ui;

/**
 * Turns the catalog's impact scores into labels a player cannot misread.
 *
 * <p>The stored numbers are "goodness": {@code latencyImpact = +3} means the mod removes a lot of
 * latency. Printed raw as "Lat +3" that reads like the mod <em>adds</em> delay, which is the
 * opposite of the truth. So the label always shows which way the real metric moves — FPS up is good,
 * latency down is good — and the colour says whether that is a win.
 */
public final class Impact {
	private Impact() {
	}

	/** e.g. {@code FPS ↑↑↑} for +3, {@code FPS ↓↓} for -2. */
	public static String fpsLabel(int fpsImpact) {
		return "FPS " + arrows(fpsImpact);
	}

	/** Latency is inverted: a positive score means the delay goes <em>down</em>. */
	public static String latencyLabel(int latencyImpact) {
		return "Latencia " + arrows(-latencyImpact);
	}

	/** Green when the change helps, red when it costs, muted when it is neutral. */
	public static int color(int impact) {
		if (impact > 0) {
			return Theme.GOOD;
		}
		return impact < 0 ? Theme.DANGER : Theme.TEXT_DIM;
	}

	/**
	 * Arrows pointing the way the metric moves.
	 *
	 * @param delta positive draws up arrows, negative down arrows
	 */
	private static String arrows(int delta) {
		if (delta == 0) {
			return "=";
		}
		String glyph = delta > 0 ? "↑" : "↓";
		return glyph.repeat(Math.min(3, Math.abs(delta)));
	}

	/** Long form for tooltips, where there is room to say it in words. */
	public static String explain(int fpsImpact, int latencyImpact) {
		StringBuilder text = new StringBuilder();
		text.append(switch (Integer.signum(fpsImpact)) {
			case 1 -> "Sube FPS";
			case -1 -> "Cuesta FPS";
			default -> "No cambia los FPS";
		});
		text.append(".  ");
		text.append(switch (Integer.signum(latencyImpact)) {
			case 1 -> "Reduce la latencia";
			case -1 -> "Anade latencia";
			default -> "No cambia la latencia";
		});
		return text.append('.').toString();
	}
}

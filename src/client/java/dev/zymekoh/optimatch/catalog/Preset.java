package dev.zymekoh.optimatch.catalog;

/** The three one-click goals offered on the "Para ti" tab. */
public enum Preset {
	MAX_FPS(
		"FPS maximos",
		"Todo lo que suba fotogramas, aunque el juego se vea mas plano.",
		0xFF5BE08A
	),
	MIN_LATENCY(
		"Latencia minima",
		"El camino mas corto entre tu raton y la pantalla: inventarios, HUD y movimiento.",
		0xFF6BC6FF
	),
	VANILLA_ENHANCED(
		"Vanilla mejorado",
		"FPS y latencia estables, con un Minecraft bonito pero competitivo para PvP.",
		0xFFD7A6FF
	),
	/**
	 * Only what the big networks' anticheats tolerate: performance, input fidelity and information
	 * you already have. Nothing that automates, reveals or changes the camera.
	 */
	COMPETITIVE_LEGIT(
		"Competitive Legit",
		"Solo lo que aceptan los anticheats fuertes. Sin automatizacion, minimapas ni freelook.",
		0xFF7FD8C8
	);

	private final String title;
	private final String description;
	private final int accent;

	Preset(String title, String description, int accent) {
		this.title = title;
		this.description = description;
		this.accent = accent;
	}

	public String title() {
		return this.title;
	}

	public String description() {
		return this.description;
	}

	public int accent() {
		return this.accent;
	}
}

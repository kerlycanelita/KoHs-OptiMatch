package dev.zymekoh.optimatch.catalog;

/**
 * What a mod actually does for performance. Modrinth only exposes a coarse "optimization" category,
 * which cannot tell "renders more frames" apart from "reacts to your mouse sooner" — this is the
 * distinction the three preset buttons are built on.
 */
public enum ModRole {
	/** Raises raw frame rate: renderer rewrites, culling, draw-call batching. */
	FPS("FPS", "Sube fotogramas por segundo"),
	/** Shortens the path from input to pixels: raw mouse input, frame pacing, GUI responsiveness. */
	LATENCY("Latencia", "Reduce el retardo entre tu accion y la pantalla"),
	/** Server-side / tick-side logic speedups: smoother TPS, fewer lag spikes. */
	TICK("Tick", "Optimiza la logica del juego y evita tirones"),
	/** Lowers RAM footprint, which matters most on 4-8 GB machines and Pojav. */
	MEMORY("Memoria", "Baja el consumo de RAM"),
	/** Network stack: reduces perceived lag on servers. */
	NETWORK("Red", "Mejora el rendimiento de red"),
	/** Makes the game look better; usually costs performance. */
	VISUAL("Visual", "Mejora el aspecto del juego"),
	/** Quality-of-life features competitive players rely on. */
	PVP("PvP", "Utilidad competitiva"),
	/** Required by other mods, no direct effect on its own. */
	LIBRARY("Libreria", "Dependencia de otros mods");

	private final String label;
	private final String explanation;

	ModRole(String label, String explanation) {
		this.label = label;
		this.explanation = explanation;
	}

	public String label() {
		return this.label;
	}

	public String explanation() {
		return this.explanation;
	}
}

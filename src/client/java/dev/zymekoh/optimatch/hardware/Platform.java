package dev.zymekoh.optimatch.hardware;

/** Where the game is running. Recommendations differ enormously between desktop and Android. */
public enum Platform {
	DESKTOP("PC"),
	POJAV("PojavLauncher (Android)"),
	UNKNOWN("Desconocido");

	private final String label;

	Platform(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

	public boolean isMobile() {
		return this == POJAV;
	}
}

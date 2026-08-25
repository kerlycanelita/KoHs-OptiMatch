package dev.zymekoh.optimatch.hardware;

/**
 * Snapshot of the machine, taken once when the menu first opens.
 *
 * @param vramMb        0 when the GPU did not report dedicated video memory
 * @param cpuMaxGhz     0 when the frequency could not be read
 */
public record HardwareProfile(
	Platform platform,
	String cpuName,
	int physicalCores,
	int logicalThreads,
	double cpuMaxGhz,
	long totalRamMb,
	long allocatedRamMb,
	String gpuName,
	String gpuVendor,
	String gpuBackend,
	long vramMb,
	int refreshRateHz,
	String osName,
	String osArch,
	String javaVersion
) {
	/** Coarse capability score (0..100) used to pick how aggressive the presets should be. */
	public int performanceScore() {
		int score = 0;

		// Threads: 4 is the floor for modern Minecraft, 16+ is comfortable.
		score += Math.min(30, this.logicalThreads * 2);

		// RAM: 8 GB is the practical baseline.
		score += (int) Math.min(25, this.totalRamMb / 700);

		// Clock speed matters more than core count for Minecraft's main thread.
		score += (int) Math.min(20, Math.round(this.cpuMaxGhz * 4.5));

		// A dedicated GPU with real VRAM is the biggest single lever.
		score += (int) Math.min(20, this.vramMb / 250);

		if (this.platform == Platform.POJAV) {
			// Mobile GPUs and the GL translation layer cost a lot of headroom.
			score = (int) (score * 0.55);
		}
		return Math.max(0, Math.min(100, score));
	}

	public boolean isLowEnd() {
		return performanceScore() < 40;
	}

	public boolean isHighRefresh() {
		return this.refreshRateHz >= 120;
	}

	public String ramSummary() {
		return this.totalRamMb <= 0
			? "desconocida"
			: String.format("%.1f GB (%d MB asignados a la JVM)", this.totalRamMb / 1024.0, this.allocatedRamMb);
	}

	public String cpuSummary() {
		String frequency = this.cpuMaxGhz > 0 ? String.format(" @ %.2f GHz", this.cpuMaxGhz) : "";
		return String.format("%s — %d nucleos / %d hilos%s", this.cpuName, this.physicalCores, this.logicalThreads, frequency);
	}

	public String gpuSummary() {
		String vram = this.vramMb > 0 ? String.format(" — %d MB VRAM", this.vramMb) : "";
		return this.gpuName + vram;
	}
}

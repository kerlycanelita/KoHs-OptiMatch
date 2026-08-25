package dev.zymekoh.optimatch.hardware;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Builds a {@link HardwareProfile}. OSHI ships with Minecraft (it backs the vanilla crash report
 * system report), so no extra dependency is needed. Every probe is defensive: a machine that
 * refuses to report a value must not break the menu.
 */
public final class HardwareScanner {
	/** Renderer substrings that only ever appear on Android GL translation layers. */
	private static final List<String> MOBILE_RENDERERS =
		List.of("gl4es", "zink", "virgl", "adreno", "mali", "powervr", "swiftshader", "angle");

	private static HardwareProfile cached;

	private HardwareScanner() {
	}

	public static HardwareProfile profile() {
		if (cached == null) {
			cached = build();
		}
		return cached;
	}

	private static HardwareProfile build() {
		String gpuName = "Desconocida";
		String gpuVendor = "";
		String gpuBackend = "";

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device != null) {
			gpuName = orUnknown(device.getRenderer(), "Desconocida");
			gpuVendor = orUnknown(device.getVendor(), "");
			gpuBackend = orUnknown(device.getBackendName(), "");
		}

		String cpuName = "Desconocida";
		int physicalCores = 0;
		int logicalThreads = Runtime.getRuntime().availableProcessors();
		double maxGhz = 0.0;
		long totalRamMb = 0;
		long vramMb = 0;

		try {
			SystemInfo systemInfo = new SystemInfo();
			HardwareAbstractionLayer hardware = systemInfo.getHardware();

			CentralProcessor processor = hardware.getProcessor();
			cpuName = orUnknown(processor.getProcessorIdentifier().getName(), "Desconocida");
			physicalCores = processor.getPhysicalProcessorCount();
			logicalThreads = processor.getLogicalProcessorCount();
			long maxHz = processor.getMaxFreq();
			if (maxHz > 0) {
				maxGhz = maxHz / 1_000_000_000.0;
			}

			GlobalMemory memory = hardware.getMemory();
			totalRamMb = memory.getTotal() / (1024 * 1024);

			// Pick the card with the most VRAM: laptops report the iGPU alongside the dGPU.
			for (GraphicsCard card : hardware.getGraphicsCards()) {
				long cardVram = card.getVRam() / (1024 * 1024);
				if (cardVram > vramMb) {
					vramMb = cardVram;
				}
			}
		} catch (Throwable throwable) {
			// OSHI can fail hard on locked-down or emulated systems; the GL data alone is still useful.
			OptiMatchClient.LOGGER.warn("Hardware probe via OSHI failed, falling back to JVM data", throwable);
		}

		long allocatedRamMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
		if (totalRamMb <= 0) {
			totalRamMb = allocatedRamMb;
		}

		int refreshRate = 0;
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null && minecraft.getWindow() != null) {
				refreshRate = minecraft.getWindow().getRefreshRate();
			}
		} catch (Throwable throwable) {
			OptiMatchClient.LOGGER.debug("Could not read the monitor refresh rate", throwable);
		}

		String osName = System.getProperty("os.name", "");
		String osArch = System.getProperty("os.arch", "");

		return new HardwareProfile(
			detectPlatform(gpuName, osName, osArch),
			cpuName,
			physicalCores > 0 ? physicalCores : logicalThreads,
			logicalThreads,
			maxGhz,
			totalRamMb,
			allocatedRamMb,
			gpuName,
			gpuVendor,
			gpuBackend,
			vramMb,
			refreshRate,
			osName,
			osArch,
			System.getProperty("java.version", "")
		);
	}

	/**
	 * PojavLauncher runs a desktop JVM on Android, so {@code os.name} still says "Linux". We look for
	 * the launcher's own environment first and fall back to Android filesystem and GL fingerprints.
	 */
	private static Platform detectPlatform(String gpuName, String osName, String osArch) {
		if (System.getenv("POJAV_NATIVEDIR") != null
			|| System.getenv("POJAV_RENDERER") != null
			|| System.getProperty("pojav.renderer") != null) {
			return Platform.POJAV;
		}

		String javaHome = System.getProperty("java.home", "").toLowerCase(Locale.ROOT);
		if (javaHome.contains("pojav") || javaHome.startsWith("/data/data/") || javaHome.startsWith("/data/user/")) {
			return Platform.POJAV;
		}

		boolean androidLike = osName.toLowerCase(Locale.ROOT).contains("linux")
			&& (osArch.contains("aarch64") || osArch.contains("arm"));
		if (androidLike && exists("/system/build.prop")) {
			return Platform.POJAV;
		}

		String renderer = gpuName.toLowerCase(Locale.ROOT);
		if (androidLike && MOBILE_RENDERERS.stream().anyMatch(renderer::contains)) {
			return Platform.POJAV;
		}

		return osName.isBlank() ? Platform.UNKNOWN : Platform.DESKTOP;
	}

	private static boolean exists(String path) {
		try {
			return Files.exists(Path.of(path));
		} catch (Exception exception) {
			return false;
		}
	}

	private static String orUnknown(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}
}

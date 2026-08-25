package dev.zymekoh.optimatch;

import dev.zymekoh.optimatch.ui.OptiMatchScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KoHs OptiMatch entry point.
 *
 * <p>The mod deliberately registers <b>no mixins of its own</b>. A tool whose job is to report
 * mixin conflicts must not add to them, so the title-screen takeover runs through the Fabric
 * lifecycle events instead of an injection.
 */
public final class OptiMatchClient implements ClientModInitializer {
	public static final String MOD_ID = "kohs_optimatch";
	public static final Logger LOGGER = LoggerFactory.getLogger("KoHs OptiMatch");

	/** The welcome menu takes over the title screen once per launch, not on every return to it. */
	private static boolean shownThisLaunch;

	@Override
	public void onInitializeClient() {
		// Waiting for a tick where the title screen is up AND the loading overlay is gone is what
		// makes the takeover stick. The overlay stays active for a while after TitleScreen.init()
		// runs, and it installs its own screen when it finishes — a screen swapped in earlier gets
		// silently discarded.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (shownThisLaunch || client.getOverlay() != null) {
				return;
			}
			if (client.screen instanceof TitleScreen titleScreen) {
				shownThisLaunch = true;
				client.setScreen(new OptiMatchScreen(titleScreen));
			}
		});

		// Performance data can be corrected without shipping a new build; this never blocks startup.
		dev.zymekoh.optimatch.catalog.Catalog.refreshFromRemote();

		LOGGER.info("KoHs OptiMatch ready — the selector opens before the main menu.");
	}

	/** Opens the selector on demand, e.g. from the Mod Menu button. */
	public static void open(Minecraft client) {
		client.setScreen(new OptiMatchScreen(client.screen));
	}

	/** Lets the player re-open the welcome flow later in the same session. */
	public static void resetLaunchFlag() {
		shownThisLaunch = false;
	}
}

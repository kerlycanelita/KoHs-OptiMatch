package dev.zymekoh.optimatch.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.optimatch.ui.OptiMatchScreen;

/**
 * Lets the player reopen the selector from Mod Menu after the launch screen has been dismissed.
 *
 * <p>Mod Menu is an optional dependency: it is {@code compileOnly}, and this class is only ever
 * loaded when Mod Menu itself resolves the {@code modmenu} entrypoint. On an instance without it,
 * nothing here is touched.
 *
 * <p>{@link OptiMatchScreen} already takes the parent screen in its constructor and returns to it on
 * close, which is exactly the contract {@link ConfigScreenFactory} expects.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return OptiMatchScreen::new;
	}
}

package dev.zymekoh.optimatch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** One page of the OptiMatch window. */
public interface OptiTab {
	/** Label drawn in the header at REGULAR and WIDE. */
	Component title();

	/** Abbreviated label for the COMPACT breakpoint, where the full title will not fit. */
	default Component shortTitle() {
		return title();
	}

	/**
	 * Called whenever the window is resized. Coordinates describe the content area below the header.
	 *
	 * @param breakpoint size class of the whole screen, so tabs can stack instead of overflowing
	 */
	void layout(int x, int y, int width, int height, Breakpoint breakpoint);

	void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity);

	default boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	/** @param key a GLFW key code; return true to stop the screen handling it as a shortcut */
	default boolean keyPressed(int key) {
		return false;
	}

	/** @param codepoint the typed character, for tabs that own a text field */
	default boolean charTyped(int codepoint) {
		return false;
	}

	/** Called once when the tab becomes visible, so heavy work happens lazily. */
	default void onSelected() {
	}
}

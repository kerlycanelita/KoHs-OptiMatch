package dev.zymekoh.optimatch.ui.dialog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A modal panel drawn over the selector. While one is open it consumes all input. */
public interface Dialog {
	void layout(int canvasWidth, int canvasHeight);

	void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity);

	boolean mouseClicked(double mouseX, double mouseY, int button);

	default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	/**
	 * @param key a GLFW key code
	 * @return true when handled; returning false lets Escape close the dialog
	 */
	default boolean keyPressed(int key, int modifiers) {
		return false;
	}

	default boolean charTyped(int codepoint) {
		return false;
	}

	/** True once the dialog wants to be dismissed. */
	boolean isClosed();

	/** A dialog opened from another one returns here when it closes, so the stack unwinds. */
	default Dialog successor() {
		return null;
	}
}

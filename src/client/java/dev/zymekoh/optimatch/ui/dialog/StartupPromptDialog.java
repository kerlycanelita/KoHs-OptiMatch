package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.config.OptiMatchSettings;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Mascot;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Asked once, on the first run: should the selector greet the player on every launch?
 *
 * <p>Each button carries a subtitle spelling out what it does. A one-word "OK" on a question about
 * recurring behaviour is exactly the kind of thing people click without reading and then resent, and
 * this preference is written to disk.
 */
public final class StartupPromptDialog implements Dialog {
	private static final long OPEN_MILLIS = 260L;

	private final long openedAt = Util.getMillis();
	private boolean closed;

	private int x;
	private int y;
	private int width;
	private int height;
	private int buttonY;
	private int buttonHeight;
	private int buttonWidth;

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(430, Math.max(240, canvasWidth - 80));
		this.height = Math.min(180, Math.max(130, canvasHeight - 120));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.buttonHeight = 30;
		this.buttonY = this.y + this.height - this.buttonHeight - 12;
		this.buttonWidth = (this.width - 32) / 2;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(150 * opacity), 0x05020B));

		float appear = Anim.easeOutBack(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.92F + 0.08F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		Draw.window(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), opacity);

		// The mascot idles beside the question rather than pacing: this is not an error.
		Mascot.renderIdle(graphics, this.x + 14, this.y + 44, 1, now, opacity);

		int textX = this.x + 44;
		int textWidth = this.width - 56;
		graphics.text(font, "Una ultima cosa", textX, this.y + 14,
			Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), true);
		Draw.wrappedText(graphics, font,
			"¿Quieres que esta pantalla se abra cada vez que inicies Minecraft?",
			textX, this.y + 26, textWidth, 2, Theme.TEXT, opacity);
		Draw.wrappedText(graphics, font,
			"Puedes cambiarlo cuando quieras, y abrirla a mano desde Mod Menu.",
			textX, this.y + 48, textWidth, 2, Theme.TEXT_DIM, opacity);

		this.renderButtons(graphics, font, mouseX, mouseY, opacity);
		graphics.pose().popMatrix();
	}

	private void renderButtons(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		for (int index = 0; index < 2; index++) {
			boolean keep = index == 1;
			int bx = this.buttonX(index);
			boolean hovered = Draw.inside(mouseX, mouseY, bx, this.buttonY, this.buttonWidth, this.buttonHeight);

			int border = hovered ? Theme.ACCENT_BRIGHT : keep ? Theme.ACCENT : Theme.BORDER;
			Draw.roundedRect(graphics, bx, this.buttonY, this.buttonWidth, this.buttonHeight, 4,
				Theme.withAlpha(border, opacity));
			Draw.roundedRect(graphics, bx + 1, this.buttonY + 1, this.buttonWidth - 2, this.buttonHeight - 2, 3,
				Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));

			String label = keep ? "Mantener pantalla inicial" : "OK";
			// The subtitle is the point: neither label says on its own what it does to the setting.
			String subtitle = keep ? "se abrira en cada arranque" : "no volver a abrirla al inicio";

			graphics.centeredText(font, font.plainSubstrByWidth(label, this.buttonWidth - 8),
				bx + this.buttonWidth / 2, this.buttonY + 7, Theme.withAlpha(Theme.TEXT, opacity));
			graphics.centeredText(font, font.plainSubstrByWidth(subtitle, this.buttonWidth - 8),
				bx + this.buttonWidth / 2, this.buttonY + 18, Theme.withAlpha(Theme.TEXT_DIM, opacity));

			if (hovered) {
				Tooltip.request(label, keep
					? "El selector seguira saludandote antes del menu principal cada vez que abras el juego."
					: "El juego ira directo al menu principal. Podras abrir el selector desde Mod Menu.",
					mouseX, mouseY);
			}
		}
	}

	private int buttonX(int index) {
		return this.x + 16 + index * (this.buttonWidth + 4);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int index = 0; index < 2; index++) {
			if (Draw.inside(mouseX, mouseY, this.buttonX(index), this.buttonY, this.buttonWidth, this.buttonHeight)) {
				OptiMatchSettings.answerStartupPrompt(index == 1);
				this.closed = true;
				return true;
			}
		}
		// Clicking away does not answer: the question is asked once and deserves a real answer.
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			// Escaping keeps the current behaviour and stops asking, which is the least surprising
			// reading of "I do not want to decide right now".
			OptiMatchSettings.answerStartupPrompt(true);
			this.closed = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}

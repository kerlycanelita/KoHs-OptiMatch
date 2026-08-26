package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.profile.ModProfile;
import dev.zymekoh.optimatch.profile.ProfileStore;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Confirmation for the two profile actions that change things on disk: installing everything a
 * profile is missing, and deleting a profile along with its mods and configs.
 *
 * <p>Both list exactly what will happen before it happens. Deletion is irreversible, so it is styled
 * as a warning and never the default button.
 */
public final class ProfileConfirmDialog implements Dialog {
	public enum Mode {
		INSTALL_MISSING, DELETE_PROFILE
	}

	private static final long OPEN_MILLIS = 200L;

	private final Mode mode;
	private final ModProfile profile;
	private final List<ModProfile.Entry> affected;
	private final Consumer<List<ModProfile.Entry>> onInstall;
	private final Runnable onDeleted;
	private final long openedAt = Util.getMillis();

	private boolean closed;
	private String result;
	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	private int x;
	private int y;
	private int width;
	private int height;
	private int listTop;
	private int listBottom;
	private int buttonY;
	private int buttonWidth;
	private int buttonHeight;

	public ProfileConfirmDialog(Mode mode, ModProfile profile, List<ModProfile.Entry> affected,
								Consumer<List<ModProfile.Entry>> onInstall, Runnable onDeleted) {
		this.mode = mode;
		this.profile = profile;
		this.affected = affected;
		this.onInstall = onInstall;
		this.onDeleted = onDeleted;
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(500, Math.max(240, canvasWidth - 80));
		this.height = Math.min(340, Math.max(160, canvasHeight - 80));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.buttonHeight = 20;
		this.buttonWidth = Math.min(150, Math.max(80, (this.width - 40) / 3));
		this.buttonY = this.y + this.height - this.buttonHeight - 10;

		this.listTop = this.y + 62;
		this.listBottom = this.buttonY - 8;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(160 * opacity), 0x05020B));

		float appear = Anim.easeOutBack(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.94F + 0.06F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		boolean destructive = this.mode == Mode.DELETE_PROFILE;
		Draw.window(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), opacity);

		this.renderHeader(graphics, font, destructive, opacity);
		this.renderList(graphics, font, opacity);
		this.renderButtons(graphics, font, mouseX, mouseY, destructive, opacity);

		graphics.pose().popMatrix();
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Font font, boolean destructive, float opacity) {
		String title = destructive
			? "Eliminar el perfil \"" + this.profile.name() + "\""
			: "Se instalaran estos mods";
		Draw.clippedText(graphics, font, title, this.x + 12, this.y + 12, this.width - 24,
			Theme.withAlpha(destructive ? Theme.DANGER : Theme.TEXT, opacity), true);

		String subtitle = destructive
			? "Se borraran tambien sus jars y sus archivos de configuracion. Esto no se puede deshacer."
			: "Del perfil \"" + this.profile.name() + "\". Se descargaran de Modrinth verificando el hash.";
		Draw.wrappedText(graphics, font, subtitle, this.x + 12, this.y + 26, this.width - 24, 2,
			destructive ? Theme.WARN : Theme.TEXT_MUTED, opacity);

		Draw.divider(graphics, this.x + 10, this.y + 54, this.width - 20, opacity);
	}

	private void renderList(GuiGraphicsExtractor graphics, Font font, float opacity) {
		graphics.enableScissor(this.x + 2, this.listTop, this.x + this.width - 2, this.listBottom);
		int cursorY = this.listTop - this.scroll.intValue();

		if (this.affected.isEmpty()) {
			graphics.centeredText(font, "Nada que hacer: ya tienes todos los mods del perfil",
				this.x + this.width / 2, this.listTop + 16, Theme.withAlpha(Theme.GOOD, opacity));
		}

		for (ModProfile.Entry entry : this.affected) {
			if (cursorY + 12 >= this.listTop && cursorY <= this.listBottom) {
				boolean reinstallable = entry.isReinstallable() || this.mode == Mode.DELETE_PROFILE;
				Draw.clippedText(graphics, font, "- " + entry.displayName() + "  v" + entry.version(),
					this.x + 14, cursorY, this.width - 40,
					Theme.withAlpha(reinstallable ? Theme.TEXT_MUTED : Theme.TEXT_DIM, opacity), false);
				if (!reinstallable) {
					// Without a Modrinth slug there is no automatic way to get it back.
					graphics.text(font, "sin origen", this.x + this.width - font.width("sin origen") - 14, cursorY,
						Theme.withAlpha(Theme.WARN, opacity), false);
				}
			}
			cursorY += 11;
		}

		if (this.mode == Mode.DELETE_PROFILE && this.profile.configCount() > 0) {
			cursorY += 6;
			if (cursorY <= this.listBottom) {
				Draw.clippedText(graphics, font,
					"y " + this.profile.configCount() + " archivos de configuracion",
					this.x + 14, cursorY, this.width - 28, Theme.withAlpha(Theme.DANGER, opacity), false);
			}
			cursorY += 12;
		}

		graphics.disableScissor();

		int content = cursorY + this.scroll.intValue() - this.listTop;
		this.maxScroll = Math.max(0, content - (this.listBottom - this.listTop));
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));
	}

	private void renderButtons(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
							   boolean destructive, float opacity) {
		if (this.result != null) {
			Draw.clippedText(graphics, font, this.result, this.x + 12, this.buttonY + 6,
				this.width - this.buttonWidth * 2 - 40, Theme.withAlpha(Theme.GOOD, opacity), false);
		}

		String[] labels = destructive
			? new String[]{"Si, eliminar todo", "Cancelar"}
			: new String[]{"Instalar", "Cancelar"};

		for (int index = 0; index < labels.length; index++) {
			int bx = this.buttonX(index);
			boolean primary = index == 0;
			boolean enabled = !(primary && this.affected.isEmpty() && !destructive);
			boolean hovered = enabled && Draw.inside(mouseX, mouseY, bx, this.buttonY, this.buttonWidth, this.buttonHeight);

			int border = !enabled ? Theme.BORDER_SOFT
				: primary && destructive ? Theme.DANGER
				: hovered ? Theme.ACCENT_BRIGHT : primary ? Theme.ACCENT : Theme.BORDER;
			Draw.roundedRect(graphics, bx, this.buttonY, this.buttonWidth, this.buttonHeight, 4,
				Theme.withAlpha(border, opacity));
			Draw.roundedRect(graphics, bx + 1, this.buttonY + 1, this.buttonWidth - 2, this.buttonHeight - 2, 3,
				Theme.withAlpha(!enabled ? Theme.PANEL : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			graphics.centeredText(font, font.plainSubstrByWidth(labels[index], this.buttonWidth - 6),
				bx + this.buttonWidth / 2, this.buttonY + (this.buttonHeight - 8) / 2,
				Theme.withAlpha(enabled ? Theme.TEXT : Theme.TEXT_DIM, opacity));

			if (hovered) {
				Tooltip.request(labels[index], primary
					? (destructive
						? "Borra el perfil, sus jars de mods/ y sus configs. Irreversible."
						: "Descarga los " + this.affected.size() + " mods que faltan y los deja listos para el proximo arranque.")
					: "No cambia nada.", mouseX, mouseY);
			}
		}
	}

	private int buttonX(int index) {
		int total = this.buttonWidth * 2 + 6;
		return this.x + this.width - total - 12 + index * (this.buttonWidth + 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int index = 0; index < 2; index++) {
			if (Draw.inside(mouseX, mouseY, this.buttonX(index), this.buttonY, this.buttonWidth, this.buttonHeight)) {
				if (index == 1) {
					this.closed = true;
					return true;
				}
				if (this.mode == Mode.DELETE_PROFILE) {
					this.result = ProfileStore.delete(this.profile.name(), true);
					this.onDeleted.run();
					this.closed = true;
				} else if (!this.affected.isEmpty()) {
					this.onInstall.accept(this.affected);
					this.closed = true;
				}
				return true;
			}
		}
		if (!Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.height)) {
			this.closed = true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll > 0) {
			this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * 12, 0, this.maxScroll));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
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

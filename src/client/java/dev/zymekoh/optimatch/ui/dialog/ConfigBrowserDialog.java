package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.config.EditableFile;
import dev.zymekoh.optimatch.config.ModConfigLocator;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The file tree for one installed mod: everything it wrote into {@code config/} that can be edited
 * by hand. Picking a file hands over to {@link ConfigEditorDialog}.
 */
public final class ConfigBrowserDialog implements Dialog {
	private static final int ROW_HEIGHT = 22;
	private static final long OPEN_MILLIS = 220L;

	private final InstalledMod mod;
	private final List<EditableFile> files;
	private final long openedAt = Util.getMillis();

	private Dialog successor;
	private boolean closed;
	private int hovered = -1;

	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	private int x;
	private int y;
	private int width;
	private int height;
	private int listTop;
	private int listBottom;
	private int closeX;
	private int closeY;
	private int closeWidth;
	private int closeHeight;

	public ConfigBrowserDialog(InstalledMod mod) {
		this.mod = mod;
		this.files = ModConfigLocator.filesFor(mod);
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(520, Math.max(240, canvasWidth - 80));
		this.height = Math.min(360, Math.max(160, canvasHeight - 70));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.closeWidth = 90;
		this.closeHeight = 20;
		this.closeX = this.x + this.width - this.closeWidth - 12;
		this.closeY = this.y + this.height - this.closeHeight - 10;

		this.listTop = this.y + 54;
		this.listBottom = this.closeY - 8;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(150 * opacity), 0x05020B));

		// Small scale-in so the panel arrives rather than blinking into place.
		float appear = Anim.easeOutBack(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.94F + 0.06F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		Draw.panel(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), Theme.withAlpha(Theme.ACCENT, opacity));

		this.renderHeader(graphics, font, opacity);
		this.renderList(graphics, font, mouseX, mouseY, opacity);
		this.renderFooter(graphics, font, mouseX, mouseY, opacity);

		graphics.pose().popMatrix();
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Font font, float opacity) {
		ModIcons.draw(graphics, font, this.mod.id(), this.mod.displayName(),
			this.x + 12, this.y + 12, 20, Theme.ACCENT, opacity);

		graphics.text(font, "Configuracion de " + this.mod.displayName(), this.x + 38, this.y + 13,
			Theme.withAlpha(Theme.TEXT, opacity), true);
		Draw.clippedText(graphics, font, "config/", this.x + 38, this.y + 25, this.width - 50,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		String count = this.files.size() + (this.files.size() == 1 ? " archivo" : " archivos");
		graphics.text(font, count, this.x + this.width - font.width(count) - 12, this.y + 13,
			Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);

		Draw.divider(graphics, this.x + 10, this.y + 46, this.width - 20, opacity);
	}

	private void renderList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		if (this.files.isEmpty()) {
			graphics.centeredText(font, "Este mod aun no ha generado archivos de configuracion",
				this.x + this.width / 2, (this.listTop + this.listBottom) / 2 - 8,
				Theme.withAlpha(Theme.TEXT_DIM, opacity));
			graphics.centeredText(font, "Muchos mods los crean la primera vez que cambias un ajuste",
				this.x + this.width / 2, (this.listTop + this.listBottom) / 2 + 4,
				Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.75F));
			return;
		}

		graphics.enableScissor(this.x + 2, this.listTop, this.x + this.width - 2, this.listBottom);
		int cursorY = this.listTop - this.scroll.intValue();
		this.hovered = -1;

		for (int index = 0; index < this.files.size(); index++) {
			EditableFile file = this.files.get(index);
			if (cursorY + ROW_HEIGHT >= this.listTop && cursorY <= this.listBottom) {
				boolean isHovered = Draw.inside(mouseX, mouseY, this.x + 8, cursorY, this.width - 16, ROW_HEIGHT - 2);
				if (isHovered) {
					this.hovered = index;
					Draw.roundedRect(graphics, this.x + 8, cursorY, this.width - 16, ROW_HEIGHT - 2, 3,
						Theme.withAlpha(Theme.PANEL_HOVER, opacity));
					Tooltip.request(file.fileName(),
						"Abrir en el editor.  " + file.relativePath() + "  ·  " + file.sizeLabel(),
						mouseX, mouseY);
				}

				// Type tag doubles as the file icon.
				int tagWidth = font.width(file.extension()) + 8;
				Draw.roundedRect(graphics, this.x + 12, cursorY + 4, tagWidth, 12, 2,
					Theme.argb(Math.round(70 * opacity), Theme.ACCENT_RGB));
				graphics.text(font, file.extension(), this.x + 16, cursorY + 6,
					Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);

				int textX = this.x + 16 + tagWidth;
				String size = file.sizeLabel();
				int sizeWidth = font.width(size);
				Draw.clippedText(graphics, font, file.relativePath(), textX, cursorY + 6,
					this.width - (textX - this.x) - sizeWidth - 24,
					Theme.withAlpha(isHovered ? Theme.TEXT : Theme.TEXT_MUTED, opacity), false);
				graphics.text(font, size, this.x + this.width - sizeWidth - 14, cursorY + 6,
					Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
			}
			cursorY += ROW_HEIGHT;
		}

		graphics.disableScissor();

		int content = this.files.size() * ROW_HEIGHT;
		this.maxScroll = Math.max(0, content - (this.listBottom - this.listTop));
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));
	}

	private void renderFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		Draw.clippedText(graphics, font, "Solo se listan archivos de este mod",
			this.x + 12, this.closeY + 6, this.width - this.closeWidth - 30,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		boolean hoveredClose = Draw.inside(mouseX, mouseY, this.closeX, this.closeY, this.closeWidth, this.closeHeight);
		Draw.roundedRect(graphics, this.closeX, this.closeY, this.closeWidth, this.closeHeight, 4,
			Theme.withAlpha(hoveredClose ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
		Draw.roundedRect(graphics, this.closeX + 1, this.closeY + 1, this.closeWidth - 2, this.closeHeight - 2, 3,
			Theme.withAlpha(hoveredClose ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
		graphics.centeredText(font, "Cerrar", this.closeX + this.closeWidth / 2,
			this.closeY + (this.closeHeight - 8) / 2, Theme.withAlpha(Theme.TEXT, opacity));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Draw.inside(mouseX, mouseY, this.closeX, this.closeY, this.closeWidth, this.closeHeight)) {
			this.closed = true;
			return true;
		}

		if (Draw.inside(mouseX, mouseY, this.x, this.listTop, this.width, this.listBottom - this.listTop)) {
			int index = (int) ((mouseY - this.listTop + this.scroll.value()) / ROW_HEIGHT);
			if (index >= 0 && index < this.files.size()) {
				// Hand over to the editor; closing it comes back to this browser.
				this.successor = new ConfigEditorDialog(this.files.get(index), this.mod);
				this.closed = true;
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
			this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * ROW_HEIGHT, 0, this.maxScroll));
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

	@Override
	public Dialog successor() {
		return this.successor;
	}
}

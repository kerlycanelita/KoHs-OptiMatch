package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.config.ConfigDocument;
import dev.zymekoh.optimatch.config.EditableFile;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.editor.Syntax;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * A small code editor for one mod config file: line numbers, syntax colouring, a caret you can
 * drive, and the two operations that matter — save, and undo everything you typed.
 *
 * <p>Reset restores the file to how it was when opened. It never invents a mod's defaults, because
 * only the mod knows those. A {@code .optimatch.bak} is written beside the file on first save.
 */
public final class ConfigEditorDialog implements Dialog {
	private static final int LINE_HEIGHT = 10;
	private static final long OPEN_MILLIS = 200L;

	private final EditableFile file;
	private final InstalledMod mod;
	private final long openedAt = Util.getMillis();

	private ConfigDocument document;
	private String error;
	private String status;
	private long statusAt;

	private Dialog successor;
	private boolean closed;
	private boolean confirmingClose;

	private final Anim.Smoothed scroll = new Anim.Smoothed(18.0F);
	private int maxScroll;
	private int gutterWidth;

	private int x;
	private int y;
	private int width;
	private int height;
	private int editorTop;
	private int editorBottom;
	private int buttonY;
	private int buttonHeight;
	private int buttonWidth;

	public ConfigEditorDialog(EditableFile file, InstalledMod mod) {
		this.file = file;
		this.mod = mod;
		try {
			this.document = ConfigDocument.open(file);
		} catch (Exception exception) {
			this.error = exception.getMessage();
			OptiMatchClient.LOGGER.warn("Could not open {} for editing", file.relativePath(), exception);
		}
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(640, Math.max(280, canvasWidth - 40));
		this.height = Math.min(430, Math.max(180, canvasHeight - 34));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.buttonHeight = 20;
		this.buttonWidth = Math.min(110, Math.max(64, (this.width - 40) / 4));
		this.buttonY = this.y + this.height - this.buttonHeight - 10;

		this.editorTop = this.y + 44;
		this.editorBottom = this.buttonY - 8;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(165 * opacity), 0x05020B));

		float appear = Anim.easeOut(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.96F + 0.04F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		Draw.panel(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), Theme.withAlpha(Theme.ACCENT, opacity));

		this.renderHeader(graphics, font, opacity);

		if (this.document == null) {
			graphics.centeredText(font, "No se puede editar este archivo",
				this.x + this.width / 2, (this.editorTop + this.editorBottom) / 2 - 6,
				Theme.withAlpha(Theme.DANGER, opacity));
			if (this.error != null) {
				graphics.centeredText(font, font.plainSubstrByWidth(this.error, this.width - 30),
					this.x + this.width / 2, (this.editorTop + this.editorBottom) / 2 + 6,
					Theme.withAlpha(Theme.TEXT_DIM, opacity));
			}
		} else {
			this.renderCode(graphics, font, mouseX, mouseY, now, opacity);
		}

		this.renderFooter(graphics, font, mouseX, mouseY, opacity);
		graphics.pose().popMatrix();
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Font font, float opacity) {
		boolean dirty = this.document != null && this.document.isDirty();
		String title = this.file.fileName() + (dirty ? " •" : "");
		graphics.text(font, title, this.x + 12, this.y + 11,
			Theme.withAlpha(dirty ? Theme.WARN : Theme.TEXT, opacity), true);

		Draw.clippedText(graphics, font, this.mod.displayName() + "  ·  config/" + this.file.relativePath(),
			this.x + 12, this.y + 23, this.width - 90, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		String format = this.file.format().name();
		graphics.text(font, format, this.x + this.width - font.width(format) - 12, this.y + 11,
			Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);

		Draw.divider(graphics, this.x + 8, this.y + 38, this.width - 16, opacity);
	}

	private void renderCode(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		List<String> lines = this.document.lines();
		this.gutterWidth = font.width(String.valueOf(Math.max(99, lines.size()))) + 10;

		int codeX = this.x + 8 + this.gutterWidth + 6;
		int codeRight = this.x + this.width - 10;

		// Editor surface, darker than the panel so it reads as a code area.
		Draw.roundedRect(graphics, this.x + 8, this.editorTop, this.width - 16,
			this.editorBottom - this.editorTop, 4, Theme.argb(Math.round(235 * opacity), 0x0E0718));
		graphics.fill(this.x + 8 + this.gutterWidth, this.editorTop, this.x + 9 + this.gutterWidth,
			this.editorBottom, Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		graphics.enableScissor(this.x + 9, this.editorTop + 1, codeRight, this.editorBottom - 1);

		int scrollOffset = this.scroll.intValue();
		int firstVisible = Math.max(0, scrollOffset / LINE_HEIGHT - 1);
		int lastVisible = Math.min(lines.size() - 1,
			(scrollOffset + (this.editorBottom - this.editorTop)) / LINE_HEIGHT + 1);

		for (int index = firstVisible; index <= lastVisible; index++) {
			int lineY = this.editorTop + 4 + index * LINE_HEIGHT - scrollOffset;
			boolean caretLine = index == this.document.caretLine();

			if (caretLine) {
				graphics.fill(this.x + 9 + this.gutterWidth, lineY - 1, codeRight, lineY + LINE_HEIGHT - 1,
					Theme.argb(Math.round(38 * opacity), Theme.ACCENT_RGB));
			}

			String number = String.valueOf(index + 1);
			graphics.text(font, number, this.x + 8 + this.gutterWidth - font.width(number) - 5, lineY,
				Theme.withAlpha(caretLine ? Theme.ACCENT_BRIGHT : Theme.TEXT_DIM, opacity * 0.9F), false);

			int spanX = codeX;
			for (Syntax.Span span : Syntax.highlight(lines.get(index), this.file.format())) {
				if (spanX > codeRight) {
					break;
				}
				graphics.text(font, span.text(), spanX, lineY, Theme.withAlpha(span.color(), opacity), false);
				spanX += font.width(span.text());
			}

			if (caretLine && (now / 500L) % 2 == 0) {
				String upToCaret = lines.get(index).substring(0, this.document.caretColumn());
				int caretX = codeX + font.width(upToCaret);
				graphics.fill(caretX, lineY - 1, caretX + 1, lineY + 9,
					Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));
			}
		}

		graphics.disableScissor();

		int content = lines.size() * LINE_HEIGHT + 8;
		this.maxScroll = Math.max(0, content - (this.editorBottom - this.editorTop));
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));

		if (this.maxScroll > 0) {
			int track = this.editorBottom - this.editorTop;
			int thumb = Math.max(16, track * track / (track + this.maxScroll));
			int thumbY = this.editorTop + Math.round((track - thumb) * (scrollOffset / (float) this.maxScroll));
			Draw.roundedRect(graphics, codeRight - 1, thumbY, 2, thumb, 1,
				Theme.withAlpha(Theme.ACCENT_DIM, opacity));
		}
	}

	private void renderFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		if (this.document != null) {
			String position = "Ln " + (this.document.caretLine() + 1) + ", Col " + (this.document.caretColumn() + 1);
			graphics.text(font, position, this.x + 12, this.buttonY + 6,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

			if (this.status != null && Util.getMillis() - this.statusAt < 4000L) {
				int statusX = this.x + 12 + font.width(position) + 10;
				Draw.clippedText(graphics, font, this.status, statusX, this.buttonY + 6,
					Math.max(10, this.buttonX(0) - statusX - 8), Theme.withAlpha(Theme.GOOD, opacity), false);
			}
		}

		String[] labels = {"Guardar", "Restablecer", this.confirmingClose ? "Salir sin guardar" : "Cerrar"};
		for (int index = 0; index < labels.length; index++) {
			int bx = this.buttonX(index);
			boolean enabled = this.isEnabled(index);
			boolean hovered = Draw.inside(mouseX, mouseY, bx, this.buttonY, this.buttonWidth, this.buttonHeight);

			int border = !enabled ? Theme.BORDER_SOFT
				: index == 2 && this.confirmingClose ? Theme.DANGER
				: hovered ? Theme.ACCENT_BRIGHT : index == 0 ? Theme.ACCENT : Theme.BORDER;
			Draw.roundedRect(graphics, bx, this.buttonY, this.buttonWidth, this.buttonHeight, 4,
				Theme.withAlpha(border, opacity));
			Draw.roundedRect(graphics, bx + 1, this.buttonY + 1, this.buttonWidth - 2, this.buttonHeight - 2, 3,
				Theme.withAlpha(!enabled ? Theme.PANEL : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			graphics.centeredText(font, font.plainSubstrByWidth(labels[index], this.buttonWidth - 6),
				bx + this.buttonWidth / 2, this.buttonY + (this.buttonHeight - 8) / 2,
				Theme.withAlpha(enabled ? Theme.TEXT : Theme.TEXT_DIM, opacity));

			if (hovered) {
				Tooltip.request(labels[index], this.tooltipFor(index), mouseX, mouseY);
			}
		}
	}

	private String tooltipFor(int index) {
		return switch (index) {
			case 0 -> "Escribe los cambios al archivo. La primera vez deja una copia .optimatch.bak al lado. "
				+ "Los mods leen su config al arrancar, asi que reinicia para que surtan efecto.";
			case 1 -> "Deshace TUS cambios y vuelve al contenido que tenia el archivo al abrirlo. "
				+ "No restaura los valores por defecto del mod.";
			default -> this.document != null && this.document.isDirty()
				? "Tienes cambios sin guardar. Pulsa otra vez para descartarlos."
				: "Vuelve a la lista de archivos.";
		};
	}

	private boolean isEnabled(int index) {
		if (this.document == null) {
			return index == 2;
		}
		return switch (index) {
			case 0, 1 -> this.document.isDirty();
			default -> true;
		};
	}

	private int buttonX(int index) {
		int total = this.buttonWidth * 3 + 12;
		return this.x + this.width - total - 12 + index * (this.buttonWidth + 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int index = 0; index < 3; index++) {
			if (Draw.inside(mouseX, mouseY, this.buttonX(index), this.buttonY, this.buttonWidth, this.buttonHeight)) {
				if (this.isEnabled(index)) {
					this.activate(index);
				}
				return true;
			}
		}

		// Clicking in the code area moves the caret there.
		if (this.document != null
			&& Draw.inside(mouseX, mouseY, this.x + 9 + this.gutterWidth, this.editorTop,
			this.width - this.gutterWidth - 20, this.editorBottom - this.editorTop)) {
			int line = (int) ((mouseY - this.editorTop - 4 + this.scroll.value()) / LINE_HEIGHT);
			line = Mth.clamp(line, 0, this.document.lineCount() - 1);

			Font font = Minecraft.getInstance().font;
			int codeX = this.x + 8 + this.gutterWidth + 6;
			String text = this.document.lines().get(line);
			int column = text.length();
			for (int index = 0; index <= text.length(); index++) {
				if (codeX + font.width(text.substring(0, index)) >= mouseX) {
					column = Math.max(0, index - 1);
					break;
				}
			}
			this.document.placeCaret(line, column);
			return true;
		}
		return true;
	}

	private void activate(int index) {
		switch (index) {
			case 0 -> {
				try {
					this.document.save();
					this.status = "Guardado. Reinicia para aplicarlo.";
					this.statusAt = Util.getMillis();
				} catch (Exception exception) {
					this.status = "Error al guardar: " + exception.getMessage();
					this.statusAt = Util.getMillis();
					OptiMatchClient.LOGGER.error("Could not save {}", this.file.relativePath(), exception);
				}
			}
			case 1 -> {
				this.document.reset();
				this.scroll.snapTo(0);
				this.status = "Cambios descartados.";
				this.statusAt = Util.getMillis();
			}
			default -> {
				// Unsaved work needs a deliberate second click before it is thrown away.
				if (this.document != null && this.document.isDirty() && !this.confirmingClose) {
					this.confirmingClose = true;
				} else {
					this.successor = new ConfigBrowserDialog(this.mod);
					this.closed = true;
				}
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll > 0) {
			this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * LINE_HEIGHT * 3, 0, this.maxScroll));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (this.document == null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				this.closed = true;
			}
			return true;
		}

		boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		if (control && key == GLFW.GLFW_KEY_S) {
			this.activate(0);
			return true;
		}
		if (control && key == GLFW.GLFW_KEY_V) {
			this.document.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			this.followCaret();
			return true;
		}

		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> this.activate(2);
			case GLFW.GLFW_KEY_BACKSPACE -> this.document.backspace();
			case GLFW.GLFW_KEY_DELETE -> this.document.delete();
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> this.document.newline();
			case GLFW.GLFW_KEY_TAB -> this.document.insertIndent();
			case GLFW.GLFW_KEY_LEFT -> this.document.moveCaret(0, -1);
			case GLFW.GLFW_KEY_RIGHT -> this.document.moveCaret(0, 1);
			case GLFW.GLFW_KEY_UP -> this.document.moveCaret(-1, 0);
			case GLFW.GLFW_KEY_DOWN -> this.document.moveCaret(1, 0);
			case GLFW.GLFW_KEY_HOME -> this.document.caretToLineStart();
			case GLFW.GLFW_KEY_END -> this.document.caretToLineEnd();
			case GLFW.GLFW_KEY_PAGE_UP -> this.document.moveCaret(-12, 0);
			case GLFW.GLFW_KEY_PAGE_DOWN -> this.document.moveCaret(12, 0);
			default -> {
				return true;
			}
		}
		this.confirmingClose = false;
		this.followCaret();
		return true;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (this.document == null) {
			return true;
		}
		this.document.insert(new String(Character.toChars(codepoint)));
		this.confirmingClose = false;
		this.followCaret();
		return true;
	}

	/** Keeps the caret on screen while typing or navigating. */
	private void followCaret() {
		int caretY = this.document.caretLine() * LINE_HEIGHT;
		int viewHeight = this.editorBottom - this.editorTop;
		float target = this.scroll.target();

		if (caretY < target) {
			target = caretY;
		} else if (caretY + LINE_HEIGHT > target + viewHeight - 6) {
			target = caretY + LINE_HEIGHT - viewHeight + 6;
		}
		this.scroll.set(Mth.clamp(target, 0, Math.max(0, this.maxScroll)));
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

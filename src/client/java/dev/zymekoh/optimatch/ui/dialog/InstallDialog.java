package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.catalog.ModrinthProject;
import dev.zymekoh.optimatch.catalog.ModrinthVersion;
import dev.zymekoh.optimatch.install.InstallPlan;
import dev.zymekoh.optimatch.install.ModInstaller;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.MarkdownView;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Mascot;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.Theme;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The confirmation shown before anything is downloaded: what the mod is, which build would be
 * installed, how big it is, what it drags in, and its documentation.
 *
 * <p>Nothing is fetched until the player presses Install, and the exact file list is on screen
 * before that point.
 */
public final class InstallDialog implements Dialog {
	private enum State {
		LOADING, READY, BLOCKED, INSTALLING, FINISHED
	}

	private final String slug;
	private final String displayName;
	private final String projectId;

	private State state = State.LOADING;
	private ModrinthProject project;
	private MarkdownView documentation;
	private InstallPlan plan;
	private final Map<String, ModInstaller.Progress> progress = new LinkedHashMap<>();

	private boolean closed;
	private int scroll;
	private int maxScroll;

	private int x;
	private int y;
	private int width;
	private int height;
	private int docTop;
	private int docBottom;
	private int buttonRowY;
	private int buttonWidth;
	private int buttonHeight;

	public InstallDialog(String slug, String displayName, String projectId) {
		this.slug = slug;
		this.displayName = displayName;
		this.projectId = projectId == null ? "" : projectId;

		ModrinthClient.details(slug).thenAccept(details -> {
			this.project = details;
			if (details != null) {
				this.documentation = new MarkdownView(details.body());
				ModIcons.requestRemote(details.projectId(), details.iconUrl());
			}
		});

		ModInstaller.plan(slug, displayName).thenAccept(resolved -> {
			this.plan = resolved;
			this.state = resolved.isBlocked() ? State.BLOCKED : State.READY;
		}).exceptionally(throwable -> {
			OptiMatchClient.LOGGER.error("Could not build an install plan for {}", slug, throwable);
			this.state = State.BLOCKED;
			return null;
		});
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(560, Math.max(220, canvasWidth - 60));
		this.height = Math.min(420, Math.max(160, canvasHeight - 50));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.buttonHeight = 20;
		this.buttonRowY = this.y + this.height - this.buttonHeight - 10;
		this.buttonWidth = Math.min(120, Math.max(70, (this.width - 40) / 3));

		this.docTop = this.y + 78;
		this.docBottom = this.buttonRowY - 8;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		// Dim everything behind the dialog so it reads as modal.
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(150 * opacity), 0x05020B));

		Draw.panel(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), Theme.withAlpha(Theme.ACCENT, opacity));

		this.renderHeader(graphics, font, opacity);

		switch (this.state) {
			case LOADING -> this.renderCentered(graphics, font, "Consultando Modrinth...", now, opacity);
			case BLOCKED -> this.renderBlocked(graphics, font, now, opacity);
			case READY -> this.renderDocumentation(graphics, font, opacity);
			case INSTALLING, FINISHED -> this.renderProgress(graphics, font, opacity);
		}

		this.renderButtons(graphics, font, mouseX, mouseY, opacity);
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Font font, float opacity) {
		int iconSize = 32;
		int iconX = this.x + 12;
		int iconY = this.y + 12;
		if (this.project != null) {
			ModIcons.drawRemote(graphics, font, this.project.projectId(), this.displayName,
				iconX, iconY, iconSize, Theme.ACCENT, opacity);
		} else {
			ModIcons.drawFallback(graphics, font, this.displayName, iconX, iconY, iconSize, Theme.ACCENT, opacity);
		}

		int textX = iconX + iconSize + 10;
		int textWidth = this.x + this.width - textX - 12;

		graphics.text(font, this.displayName, textX, this.y + 14, Theme.withAlpha(Theme.TEXT, opacity), true);

		if (this.project != null) {
			Draw.clippedText(graphics, font, this.project.description(), textX, this.y + 26, textWidth,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			Draw.clippedText(graphics, font, this.project.downloadsLabel(), textX, this.y + 37, textWidth,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		}

		// The facts that decide whether this is safe to install.
		int factsY = this.y + 52;
		if (this.plan != null && !this.plan.files().isEmpty()) {
			ModrinthVersion main = this.plan.files().get(0);
			int badgeX = textX;
			badgeX += Draw.badge(graphics, font, main.channel().label(), badgeX, factsY,
				switch (main.channel()) {
					case RELEASE -> Theme.GOOD;
					case BETA -> Theme.WARN;
					case ALPHA -> Theme.DANGER;
				}, opacity) + 4;
			badgeX += Draw.badge(graphics, font, "MC " + main.gameVersion(), badgeX, factsY, Theme.INFO, opacity) + 4;
			Draw.clippedText(graphics, font,
				this.plan.totalSizeLabel() + (this.plan.dependencyCount() > 0
					? "  ·  " + this.plan.dependencyCount() + " dependencias" : ""),
				badgeX, factsY + 1, Math.max(10, this.x + this.width - badgeX - 12),
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		}

		Draw.divider(graphics, this.x + 10, this.y + 70, this.width - 20, opacity);
	}

	private void renderCentered(GuiGraphicsExtractor graphics, Font font, String message, long now, float opacity) {
		int centerX = this.x + this.width / 2;
		int centerY = (this.docTop + this.docBottom) / 2;
		graphics.centeredText(font, message, centerX, centerY - 8, Theme.withAlpha(Theme.TEXT_MUTED, opacity));

		int barWidth = Math.min(180, this.width - 60);
		int barX = centerX - barWidth / 2;
		Draw.roundedRect(graphics, barX, centerY + 6, barWidth, 4, 2, Theme.withAlpha(Theme.PANEL, opacity));
		int sweep = Math.round(Draw.cycle(now, 1300L) * Math.max(1, barWidth - 40));
		Draw.roundedRect(graphics, barX + sweep, centerY + 6, 40, 4, 2, Theme.withAlpha(Theme.ACCENT, opacity));
	}

	private void renderBlocked(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		int textX = this.x + 12;
		int maxWidth = this.width - 24;
		int cursorY = this.docTop;

		// A connection failure is not the mod's fault, so it gets the friendly treatment.
		boolean offline = this.plan != null && this.plan.blockers().stream()
			.anyMatch(blocker -> blocker.toLowerCase(java.util.Locale.ROOT).contains("conexion"));
		if (offline) {
			Mascot.renderErrorState(graphics, font, this.x, this.docTop, this.width, this.docBottom - this.docTop,
				"No se pudo consultar Modrinth",
				"Sin conexion no se puede comprobar que exista una version para tu Minecraft, "
					+ "y no se instala nada a ciegas.", now, opacity);
			return;
		}

		cursorY = Draw.sectionHeader(graphics, font, "No se puede instalar", textX, cursorY, maxWidth,
			Theme.DANGER, opacity);

		if (this.plan != null) {
			for (String blocker : this.plan.blockers()) {
				if (cursorY > this.docBottom - 10) {
					break;
				}
				Draw.clippedText(graphics, font, "- " + blocker, textX + 4, cursorY, maxWidth - 4,
					Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
				cursorY += 11;
			}
		}
		cursorY += 6;
		Draw.clippedText(graphics, font, "No se ha descargado nada.", textX, cursorY, maxWidth,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
	}

	private void renderDocumentation(GuiGraphicsExtractor graphics, Font font, float opacity) {
		int textX = this.x + 12;
		int maxWidth = this.width - 24;

		graphics.enableScissor(this.x + 2, this.docTop, this.x + this.width - 2, this.docBottom);
		int cursorY = this.docTop - this.scroll;

		cursorY = Draw.sectionHeader(graphics, font, "Se descargara", textX, cursorY, maxWidth,
			Theme.ACCENT_BRIGHT, opacity);
		if (this.plan != null) {
			for (ModrinthVersion file : this.plan.files()) {
				Draw.clippedText(graphics, font, "- " + file.fileName(), textX + 4, cursorY, maxWidth - 60,
					Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
				String size = file.sizeLabel();
				if (!size.isEmpty()) {
					graphics.text(font, size, this.x + this.width - 12 - font.width(size), cursorY,
						Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
				}
				cursorY += 11;
			}
		}
		cursorY += 8;

		if (this.documentation != null && !this.documentation.isEmpty()) {
			cursorY = Draw.sectionHeader(graphics, font, "Documentacion", textX, cursorY, maxWidth,
				Theme.ACCENT_BRIGHT, opacity);

			// Say what this viewer leaves out rather than silently showing a poorer version.
			if (this.documentation.strippedImages() > 0) {
				cursorY = Draw.wrappedText(graphics, font,
					this.documentation.strippedImages() + " imagenes no se muestran aqui — "
						+ "usa \"Ver en Modrinth\" para leerla completa en el navegador.",
					textX, cursorY, maxWidth, 2, Theme.WARN, opacity);
				cursorY += 3;
			}
			this.documentation.render(graphics, font, textX, cursorY, maxWidth,
				this.docTop, this.docBottom, opacity);
			cursorY += this.documentation.contentHeight(font, maxWidth);
		}

		graphics.disableScissor();

		int content = cursorY + this.scroll - this.docTop;
		this.maxScroll = Math.max(0, content - (this.docBottom - this.docTop) + 10);
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	private void renderProgress(GuiGraphicsExtractor graphics, Font font, float opacity) {
		int textX = this.x + 12;
		int maxWidth = this.width - 24;
		int cursorY = this.docTop;

		cursorY = Draw.sectionHeader(graphics, font,
			this.state == State.FINISHED ? "Resultado" : "Descargando", textX, cursorY, maxWidth,
			Theme.ACCENT_BRIGHT, opacity);

		for (Map.Entry<String, ModInstaller.Progress> entry : this.progress.entrySet()) {
			if (cursorY > this.docBottom - 20) {
				break;
			}
			ModInstaller.Progress value = entry.getValue();
			int color = switch (value.stage()) {
				case DONE -> Theme.GOOD;
				case FAILED -> Theme.DANGER;
				case SKIPPED_ALREADY_INSTALLED -> Theme.WARN;
				default -> Theme.TEXT_MUTED;
			};

			Draw.clippedText(graphics, font, entry.getKey(), textX, cursorY, maxWidth,
				Theme.withAlpha(color, opacity), false);
			cursorY += 10;

			if (value.stage() == ModInstaller.Progress.Stage.DOWNLOADING
				|| value.stage() == ModInstaller.Progress.Stage.VERIFYING) {
				Draw.bar(graphics, textX, cursorY, maxWidth, 4, value.fraction(),
					Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.ACCENT, opacity));
				cursorY += 8;
			} else if (!value.message().isEmpty()) {
				Draw.clippedText(graphics, font, value.message(), textX + 6, cursorY, maxWidth - 6,
					Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
				cursorY += 10;
			}
			cursorY += 3;
		}

		if (this.state == State.FINISHED) {
			cursorY += 4;
			Draw.clippedText(graphics, font,
				"Reinicia Minecraft para que Fabric los cargue.", textX, cursorY, maxWidth,
				Theme.withAlpha(Theme.WARN, opacity), false);
		}
	}

	private void renderButtons(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		String[] labels = this.buttonLabels();

		// The Modrinth page is a link, not an action: an icon button in the footer's left edge.
		int linkWidth = font.width("Ver en Modrinth") + 10;
		boolean linkHovered = Draw.inside(mouseX, mouseY, this.x + 12, this.buttonRowY, linkWidth, this.buttonHeight);
		graphics.text(font, "Ver en Modrinth", this.x + 17, this.buttonRowY + (this.buttonHeight - 8) / 2,
			Theme.withAlpha(linkHovered ? Theme.ACCENT_BRIGHT : Theme.TEXT_DIM, opacity), false);
		if (linkHovered) {
			graphics.fill(this.x + 17, this.buttonRowY + this.buttonHeight / 2 + 5,
				this.x + 17 + font.width("Ver en Modrinth"), this.buttonRowY + this.buttonHeight / 2 + 6,
				Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));
			Tooltip.request("Abrir en el navegador",
				"Abre la pagina del proyecto en Modrinth para leer todo, ver capturas y el changelog.",
				mouseX, mouseY);
		}

		for (int index = 0; index < labels.length; index++) {
			int bx = this.buttonX(index);
			boolean enabled = this.isButtonEnabled(index);
			boolean hovered = enabled && Draw.inside(mouseX, mouseY, bx, this.buttonRowY, this.buttonWidth, this.buttonHeight);
			boolean primary = index == 0;

			int border = !enabled ? Theme.BORDER_SOFT : hovered ? Theme.ACCENT_BRIGHT : primary ? Theme.ACCENT : Theme.BORDER;
			int fill = !enabled ? Theme.PANEL : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED;

			Draw.roundedRect(graphics, bx, this.buttonRowY, this.buttonWidth, this.buttonHeight, 4,
				Theme.withAlpha(border, opacity));
			Draw.roundedRect(graphics, bx + 1, this.buttonRowY + 1, this.buttonWidth - 2, this.buttonHeight - 2, 3,
				Theme.withAlpha(fill, opacity));
			graphics.centeredText(font, font.plainSubstrByWidth(labels[index], this.buttonWidth - 6),
				bx + this.buttonWidth / 2, this.buttonRowY + (this.buttonHeight - 8) / 2,
				Theme.withAlpha(enabled ? Theme.TEXT : Theme.TEXT_DIM, opacity));

			if (hovered || (!enabled && Draw.inside(mouseX, mouseY, bx, this.buttonRowY, this.buttonWidth, this.buttonHeight))) {
				Tooltip.request(labels[index], this.buttonTooltip(index), mouseX, mouseY);
			}
		}
	}

	/**
	 * Only the two decisions that matter. "Descarga directa" was doing the same job as Install by a
	 * worse route, and the Modrinth page is a link, not a decision — it lives as an icon in the header.
	 */
	private String[] buttonLabels() {
		return this.state == State.FINISHED
			? new String[]{"Hecho", "Cerrar"}
			: new String[]{"Instalar", "Cancelar"};
	}

	private String buttonTooltip(int index) {
		if (index == 0) {
			if (this.state == State.FINISHED) {
				return "Cierra esta ventana. Los mods se activaran al reiniciar Minecraft.";
			}
			if (this.plan == null) {
				return "Esperando a Modrinth para saber que archivos hacen falta.";
			}
			if (this.plan.isBlocked()) {
				return "No se puede instalar: falta build para tu version de Minecraft.";
			}
			return "Descarga " + this.plan.files().size() + " archivo(s) (" + this.plan.totalSizeLabel()
				+ ") a tu carpeta mods/, verificando el hash SHA-512 de cada uno.";
		}
		return this.state == State.INSTALLING
			? "No se puede cancelar con una descarga en curso."
			: "Cierra sin descargar nada.";
	}

	private boolean isButtonEnabled(int index) {
		if (index == 0) {
			return this.state == State.READY || this.state == State.FINISHED;
		}
		return this.state != State.INSTALLING;
	}

	private int buttonX(int index) {
		int total = this.buttonWidth * 2 + 6;
		int startX = this.x + this.width - total - 12;
		return startX + index * (this.buttonWidth + 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int linkWidth = 90;
		if (Draw.inside(mouseX, mouseY, this.x + 12, this.buttonRowY, linkWidth, this.buttonHeight)) {
			openUri(this.project != null ? this.project.pageUrl() : "https://modrinth.com/mod/" + this.slug);
			return true;
		}
		for (int index = 0; index < 2; index++) {
			if (Draw.inside(mouseX, mouseY, this.buttonX(index), this.buttonRowY, this.buttonWidth, this.buttonHeight)) {
				if (this.isButtonEnabled(index)) {
					this.activate(index);
				}
				return true;
			}
		}
		// Clicking outside the card dismisses it, unless a download is in flight.
		if (!Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.height) && this.state != State.INSTALLING) {
			this.closed = true;
		}
		return true;
	}

	private void activate(int index) {
		if (index == 0 && this.state != State.FINISHED) {
			this.startInstall();
		} else {
			this.closed = true;
		}
	}

	private void startInstall() {
		if (this.plan == null || this.plan.isBlocked()) {
			return;
		}
		this.state = State.INSTALLING;
		this.progress.clear();

		ModInstaller.execute(this.plan, update -> this.progress.put(update.fileName(), update))
			.thenAccept(results -> this.state = State.FINISHED)
			.exceptionally(throwable -> {
				OptiMatchClient.LOGGER.error("Install failed for {}", this.slug, throwable);
				this.state = State.FINISHED;
				return null;
			});
	}

	private static void openUri(String url) {
		try {
			Util.getPlatform().openUri(URI.create(url));
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.warn("Could not open {}", url, exception);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0 || this.state != State.READY) {
			return true;
		}
		this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * 18), 0, this.maxScroll);
		return true;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}

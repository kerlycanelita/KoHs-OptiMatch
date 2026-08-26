package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.catalog.ModrinthProject;
import dev.zymekoh.optimatch.install.InstalledCheck;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Mascot;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import dev.zymekoh.optimatch.ui.dialog.InstallDialog;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Tab 3 — search the whole Modrinth catalogue.
 *
 * <p>The query is filtered server-side to Fabric builds for the running Minecraft version, so every
 * result is installable. A standing notice makes clear that this is the Modrinth API and that mods
 * hosted elsewhere will not show up.
 */
public final class ModsSearchTab implements OptiTab {
	private static final int PAGE_SIZE = 20;
	/** Typing keeps resetting this; the request only fires once the player pauses. */
	private static final long DEBOUNCE_MILLIS = 350L;

	private final Consumer<Dialog> dialogOpener;

	private String query = "";
	private boolean focused = true;
	private long lastEdit;
	private boolean searchPending;
	private boolean loading;
	/** Set when the request itself failed, which is a different story from zero matches. */
	private boolean offline;
	private List<ModrinthProject> results = List.of();
	private int page;
	private boolean loadedOnce;

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private int x;
	private int y;
	private int width;
	private int height;
	private int searchBoxHeight;
	private int noticeHeight;
	private int listTop;
	private int rowHeight;
	private int detailLines;
	private int iconSize;

	private int scroll;
	private int maxScroll;

	public ModsSearchTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

	@Override
	public Component title() {
		return Component.literal("Mods");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Buscar");
	}

	@Override
	public void onSelected() {
		if (!this.loadedOnce) {
			this.loadedOnce = true;
			this.runSearch();
		}
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;

		this.searchBoxHeight = 20;
		this.noticeHeight = breakpoint.isCompact() ? 20 : 16;
		this.listTop = y + this.searchBoxHeight + this.noticeHeight + 8;
		// Derived from the lines actually drawn: 5 top pad + 10 per line + 5 bottom pad. The old
		// fixed heights left the downloads line hanging two pixels into the next row.
		this.detailLines = breakpoint.isCompact() ? 2 : 3;
		this.rowHeight = 10 + this.detailLines * 10;
		this.iconSize = breakpoint.pick(20, 24, 28);
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		// Fire the debounced query once typing settles.
		if (this.searchPending && now - this.lastEdit >= DEBOUNCE_MILLIS) {
			this.searchPending = false;
			this.runSearch();
		}

		this.renderSearchBox(graphics, font, mouseX, mouseY, now, opacity);
		this.renderNotice(graphics, font, opacity);
		this.renderResults(graphics, font, mouseX, mouseY, now, opacity);
	}

	private void renderSearchBox(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		boolean hovered = Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.searchBoxHeight);
		int border = this.focused ? Theme.ACCENT_BRIGHT : hovered ? Theme.ACCENT : Theme.BORDER_SOFT;

		Draw.roundedRect(graphics, this.x, this.y, this.width, this.searchBoxHeight, 4, Theme.withAlpha(border, opacity));
		Draw.roundedRect(graphics, this.x + 1, this.y + 1, this.width - 2, this.searchBoxHeight - 2, 3,
			Theme.withAlpha(Theme.PANEL, opacity));

		int textX = this.x + 8;
		int textY = this.y + (this.searchBoxHeight - 8) / 2;

		// The status counter claims the right end first, and the query is bounded by where it starts.
		// A fixed reservation of 60px was narrower than "20 resultados", so long queries ran under it.
		String status = this.loading ? "buscando..." : this.offline ? "sin conexion" : this.results.size() + " resultados";
		int statusX = this.x + this.width - font.width(status) - 8;
		graphics.text(font, status, statusX, textY, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		int queryWidth = Math.max(20, statusX - textX - 8);
		if (this.query.isEmpty() && !this.focused) {
			Draw.clippedText(graphics, font, "Buscar mods en Modrinth...", textX, textY, queryWidth,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		} else {
			Draw.clippedText(graphics, font, this.query, textX, textY, queryWidth,
				Theme.withAlpha(Theme.TEXT, opacity), false);
			if (this.focused && (now / 500L) % 2 == 0) {
				int caretX = textX + Math.min(font.width(this.query), queryWidth - 2);
				graphics.fill(caretX + 1, textY - 1, caretX + 2, textY + 9, Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));
			}
		}
	}

	private void renderNotice(GuiGraphicsExtractor graphics, Font font, float opacity) {
		int noticeY = this.y + this.searchBoxHeight + 4;
		Draw.roundedRect(graphics, this.x, noticeY, this.width, this.noticeHeight, 3,
			Theme.argb(Math.round(45 * opacity), Theme.WARN & 0xFFFFFF));

		String notice = this.breakpoint.isCompact()
			? "Solo Modrinth. Otros sitios no aparecen."
			: "Datos de la API de Modrinth — los mods alojados fuera de Modrinth no apareceran aqui.";
		Draw.clippedText(graphics, font, notice, this.x + 6, noticeY + (this.noticeHeight - 8) / 2,
			this.width - 12, Theme.withAlpha(Theme.WARN, opacity), false);
	}

	private void renderResults(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		int listBottom = this.y + this.height;
		Draw.panel(graphics, this.x, this.listTop, this.width, Math.max(20, listBottom - this.listTop), 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		if (this.loading && this.results.isEmpty()) {
			int centerY = (this.listTop + listBottom) / 2;
			graphics.centeredText(font, Component.literal("Consultando Modrinth..."),
				this.x + this.width / 2, centerY - 8, Theme.withAlpha(Theme.TEXT_MUTED, opacity));
			int barWidth = Math.min(180, this.width - 40);
			int barX = this.x + (this.width - barWidth) / 2;
			Draw.roundedRect(graphics, barX, centerY + 6, barWidth, 4, 2, Theme.withAlpha(Theme.PANEL_RAISED, opacity));
			int sweep = Math.round(Draw.cycle(now, 1300L) * Math.max(1, barWidth - 40));
			Draw.roundedRect(graphics, barX + sweep, centerY + 6, 40, 4, 2, Theme.withAlpha(Theme.ACCENT, opacity));
			return;
		}

		if (this.offline) {
			Mascot.renderErrorState(graphics, font, this.x, this.listTop, this.width, listBottom - this.listTop,
				"No se pudo conectar con Modrinth",
				"Comprueba tu conexion. Los mods que ya tienes instalados se siguen analizando sin internet.",
				now, opacity);
			return;
		}

		if (this.results.isEmpty()) {
			graphics.centeredText(font, Component.literal("Sin resultados para esta busqueda"),
				this.x + this.width / 2, (this.listTop + listBottom) / 2 - 4,
				Theme.withAlpha(Theme.TEXT_DIM, opacity));
			return;
		}

		graphics.enableScissor(this.x + 1, this.listTop + 1, this.x + this.width - 1, listBottom - 1);
		int cursorY = this.listTop + 4 - this.scroll;

		for (ModrinthProject project : this.results) {
			if (cursorY + this.rowHeight >= this.listTop && cursorY <= listBottom) {
				boolean hovered = Draw.inside(mouseX, mouseY, this.x + 4, cursorY, this.width - 8, this.rowHeight - 3);
				Draw.roundedRect(graphics, this.x + 4, cursorY, this.width - 8, this.rowHeight - 3, 4,
					Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));

				int iconX = this.x + 9;
				int iconY = cursorY + (this.rowHeight - 3 - this.iconSize) / 2;
				ModIcons.drawRemote(graphics, font, project.projectId(), project.title(),
					iconX, iconY, this.iconSize, Theme.ACCENT, opacity);

				int textX = iconX + this.iconSize + 8;
				int installWidth = this.breakpoint.isCompact() ? 44 : 58;
				int textWidth = Math.max(20, this.x + this.width - textX - installWidth - 16);

				Draw.clippedText(graphics, font, project.title(), textX, cursorY + 5, textWidth,
					Theme.withAlpha(Theme.TEXT, opacity), false);
				Draw.clippedText(graphics, font, project.description(), textX, cursorY + 15, textWidth,
					Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
				if (this.detailLines >= 3) {
					Draw.clippedText(graphics, font, project.downloadsLabel(), textX, cursorY + 25, textWidth,
						Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.8F), false);
				}

				// Install affordance on the right of every row.
				int buttonX = this.x + this.width - installWidth - 10;
				int buttonY = cursorY + (this.rowHeight - 3 - 16) / 2;
				boolean buttonHovered = Draw.inside(mouseX, mouseY, buttonX, buttonY, installWidth, 16);
				Draw.roundedRect(graphics, buttonX, buttonY, installWidth, 16, 3,
					Theme.withAlpha(buttonHovered ? Theme.ACCENT_BRIGHT : Theme.ACCENT, opacity));
				Draw.roundedRect(graphics, buttonX + 1, buttonY + 1, installWidth - 2, 14, 2,
					Theme.withAlpha(buttonHovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
				graphics.centeredText(font, this.breakpoint.isCompact() ? "Ver" : "Instalar",
					buttonX + installWidth / 2, buttonY + 4, Theme.withAlpha(Theme.TEXT, opacity));
			}
			cursorY += this.rowHeight;
		}

		graphics.disableScissor();

		int content = this.results.size() * this.rowHeight + 8;
		this.maxScroll = Math.max(0, content - (listBottom - this.listTop));
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	private void runSearch() {
		this.loading = true;
		String snapshot = this.query;
		ModrinthClient.search(snapshot, this.page * PAGE_SIZE, PAGE_SIZE).thenAccept(found -> {
			// Discard a reply for a query the player has already changed.
			if (snapshot.equals(this.query)) {
				this.results = found;
				this.loading = false;
				this.offline = false;
				this.scroll = 0;
				this.loadIcons(found);
			}
		}).exceptionally(throwable -> {
			this.results = List.of();
			this.loading = false;
			this.offline = true;
			return null;
		});
	}

	/**
	 * Search results only carry the WebP thumbnail, which cannot be decoded, and the PNG is not
	 * always named {@code icon.png}. One bulk project lookup gets the real URLs for the whole page.
	 */
	private void loadIcons(List<ModrinthProject> hits) {
		if (hits.isEmpty()) {
			return;
		}
		List<String> ids = new java.util.ArrayList<>();
		for (ModrinthProject hit : hits) {
			if (!hit.projectId().isBlank()) {
				ids.add(hit.projectId());
			}
		}
		ModrinthClient.projects(ids).thenAccept(projects -> {
			for (ModrinthProject project : projects.values()) {
				ModIcons.requestRemote(project.projectId(), project.iconUrl());
			}
		});
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.searchBoxHeight)) {
			this.focused = true;
			return true;
		}
		this.focused = false;

		int listBottom = this.y + this.height;
		if (!Draw.inside(mouseX, mouseY, this.x, this.listTop, this.width, listBottom - this.listTop)) {
			return false;
		}

		int index = (int) ((mouseY - this.listTop - 4 + this.scroll) / this.rowHeight);
		if (index >= 0 && index < this.results.size()) {
			ModrinthProject project = this.results.get(index);
			this.dialogOpener.accept(new InstallDialog(project.slug(), project.title(), project.projectId()));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0
			|| !Draw.inside(mouseX, mouseY, this.x, this.listTop, this.width, this.y + this.height - this.listTop)) {
			return false;
		}
		this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * this.rowHeight), 0, this.maxScroll);
		return true;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!this.focused) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			if (!this.query.isEmpty()) {
				this.query = this.query.substring(0, this.query.length() - 1);
				this.scheduleSearch();
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			this.searchPending = false;
			this.page = 0;
			this.runSearch();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			this.focused = false;
			return true;
		}
		// Swallow the rest so tab-switch shortcuts do not fire while typing.
		return true;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!this.focused || this.query.length() >= 60) {
			return false;
		}
		this.query += new String(Character.toChars(codepoint));
		this.scheduleSearch();
		return true;
	}

	private void scheduleSearch() {
		this.lastEdit = Util.getMillis();
		this.searchPending = true;
		this.page = 0;
	}
}

package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.catalog.Catalog;
import dev.zymekoh.optimatch.config.ModConfigLocator;
import dev.zymekoh.optimatch.catalog.CatalogEntry;
import dev.zymekoh.optimatch.catalog.ModRole;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.dialog.ConfigBrowserDialog;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 1 — every mod in the folder on the left; on the right, the selected mod in detail and what the
 * whole set does together.
 *
 * <p>At {@link Breakpoint#COMPACT} the two panes stack vertically instead of shrinking into
 * unreadable columns.
 */
public final class InstalledModsTab implements OptiTab {
	private final Consumer<Dialog> dialogOpener;

	private List<InstalledMod> mods = List.of();
	private List<InstalledMod> userMods = List.of();

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private boolean stacked;
	private int rowHeight = 30;
	private int iconSize = 18;

	private int x;
	private int y;
	private int width;
	private int height;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private int detailX;
	private int detailY;
	private int detailWidth;
	private int detailHeight;

	private int scroll;
	private int maxScroll;
	private int detailScroll;
	private int detailMaxScroll;
	private int selected;

	/** Rectangle of the "edit config" button, recorded during render for hit testing. */
	private int configButtonX;
	private int configButtonY;
	private int configButtonWidth;
	private int configButtonHeight;
	private boolean configButtonVisible;

	public InstalledModsTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

	@Override
	public Component title() {
		return Component.literal("Mods instalados");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Mods");
	}

	@Override
	public void onSelected() {
		if (this.mods.isEmpty()) {
			this.mods = ModScanner.scan();
			List<InstalledMod> user = new ArrayList<>();
			for (InstalledMod mod : this.mods) {
				if (mod.isUserFacing()) {
					user.add(mod);
				}
			}
			this.userMods = List.copyOf(user);
		}
		// Uploading textures has to happen on the render thread, which is where onSelected runs.
		ModIcons.preload(this.userMods);
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;
		this.stacked = breakpoint.isCompact();
		// Rows are two lines now (name + description), so they need more room.
		this.rowHeight = breakpoint.pick(26, 30, 32);
		this.iconSize = breakpoint.pick(16, 18, 20);

		if (this.stacked) {
			int listShare = Math.max(60, Math.round(height * 0.55F));
			this.listX = x;
			this.listY = y;
			this.listWidth = width;
			this.listHeight = listShare;
			this.detailX = x;
			this.detailY = y + listShare + 4;
			this.detailWidth = width;
			this.detailHeight = Math.max(24, height - listShare - 4);
		} else {
			int split = Math.max(140, Math.round(width * breakpoint.pick(0.5F, 0.46F, 0.42F)));
			this.listX = x;
			this.listY = y;
			this.listWidth = split;
			this.listHeight = height;
			this.detailX = x + split + 6;
			this.detailY = y;
			this.detailWidth = Math.max(90, width - split - 6);
			this.detailHeight = height;
		}
		this.recomputeScrollBounds();
	}

	private void recomputeScrollBounds() {
		int content = this.userMods.size() * this.rowHeight;
		this.maxScroll = Math.max(0, content - (this.listHeight - 22));
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	/** Latest cursor position, so the detail pane can hit-test its own button. */
	private int hoverX;
	private int hoverY;

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		this.hoverX = mouseX;
		this.hoverY = mouseY;
		this.recomputeScrollBounds();
		this.renderList(graphics, font, mouseX, mouseY, opacity);
		this.renderDetail(graphics, font, opacity);
	}

	private void renderList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		Draw.panel(graphics, this.listX, this.listY, this.listWidth, this.listHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		String header = this.breakpoint.isCompact()
			? this.userMods.size() + " mods"
			: this.userMods.size() + " mods en tu carpeta";
		graphics.text(font, header, this.listX + 8, this.listY + 7,
			Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);

		int listTop = this.listY + 20;
		int listBottom = this.listY + this.listHeight - 2;
		graphics.enableScissor(this.listX + 1, listTop, this.listX + this.listWidth - 1, listBottom);

		for (int index = 0; index < this.userMods.size(); index++) {
			int rowY = listTop + index * this.rowHeight - this.scroll;
			if (rowY + this.rowHeight < listTop || rowY > listBottom) {
				continue;
			}

			InstalledMod mod = this.userMods.get(index);
			boolean isSelected = index == this.selected;
			boolean hovered = Draw.inside(mouseX, mouseY, this.listX + 3, rowY, this.listWidth - 6, this.rowHeight - 2);

			if (isSelected || hovered) {
				Draw.roundedRect(graphics, this.listX + 3, rowY, this.listWidth - 6, this.rowHeight - 2, 3,
					Theme.withAlpha(isSelected ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			}
			if (isSelected) {
				graphics.fill(this.listX + 3, rowY, this.listX + 5, rowY + this.rowHeight - 2,
					Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));
			}

			CatalogEntry known = Catalog.findInstalled(mod.id());
			int accent = known != null ? roleColor(known.primaryRole()) : Theme.ACCENT_DIM;

			int iconX = this.listX + 8;
			int iconY = rowY + 4;
			ModIcons.draw(graphics, font, mod.id(), mod.displayName(), iconX, iconY, this.iconSize, accent, opacity);

			int textX = iconX + this.iconSize + 6;
			int textWidth = this.listX + this.listWidth - textX - 8;

			Draw.clippedText(graphics, font, mod.displayName(), textX, rowY + 4, textWidth,
				Theme.withAlpha(isSelected ? Theme.TEXT : Theme.TEXT_MUTED, opacity), false);

			// The mod's own description, straight out of its fabric.mod.json.
			String subtitle = mod.description().isBlank() ? "v" + mod.version() : mod.description();
			Draw.clippedText(graphics, font, subtitle, textX, rowY + 14, textWidth,
				Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.9F), false);
		}

		graphics.disableScissor();

		if (this.maxScroll > 0) {
			int trackHeight = listBottom - listTop;
			int thumbHeight = Math.max(14, trackHeight * trackHeight / (trackHeight + this.maxScroll));
			int thumbY = listTop + Math.round((trackHeight - thumbHeight) * (this.scroll / (float) this.maxScroll));
			Draw.roundedRect(graphics, this.listX + this.listWidth - 4, thumbY, 2, thumbHeight, 1,
				Theme.withAlpha(Theme.ACCENT_DIM, opacity));
		}
	}

	/** Right-hand pane: the selected mod in full, then the folder-wide verdict. */
	private void renderDetail(GuiGraphicsExtractor graphics, Font font, float opacity) {
		Draw.panel(graphics, this.detailX, this.detailY, this.detailWidth, this.detailHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		int textX = this.detailX + 8;
		int maxWidth = this.detailWidth - 16;
		int bottom = this.detailY + this.detailHeight;

		graphics.enableScissor(this.detailX + 1, this.detailY + 1, this.detailX + this.detailWidth - 1, bottom - 1);
		int cursorY = this.detailY + 7 - this.detailScroll;

		InstalledMod mod = this.selectedMod();
		if (mod != null) {
			CatalogEntry known = Catalog.findInstalled(mod.id());
			int accent = known != null ? roleColor(known.primaryRole()) : Theme.ACCENT_DIM;

			ModIcons.draw(graphics, font, mod.id(), mod.displayName(), textX, cursorY, 24, accent, opacity);
			int headX = textX + 30;
			int headWidth = maxWidth - 30;

			Draw.clippedText(graphics, font, mod.displayName(), headX, cursorY + 1, headWidth,
				Theme.withAlpha(Theme.TEXT, opacity), false);
			Draw.clippedText(graphics, font, "v" + mod.version(), headX, cursorY + 11, headWidth,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
			if (!mod.authors().isBlank()) {
				Draw.clippedText(graphics, font, "por " + mod.authors(), headX, cursorY + 21, headWidth,
					Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.85F), false);
			}
			cursorY += 34;

			// Config editing is only offered when the mod has actually written something.
			this.configButtonVisible = !ModConfigLocator.filesFor(mod).isEmpty();
			if (this.configButtonVisible) {
				this.configButtonWidth = Math.min(150, maxWidth);
				this.configButtonHeight = 16;
				this.configButtonX = textX;
				this.configButtonY = cursorY;

				boolean hovered = Draw.inside(this.hoverX, this.hoverY,
					this.configButtonX, this.configButtonY, this.configButtonWidth, this.configButtonHeight);
				Draw.roundedRect(graphics, this.configButtonX, this.configButtonY,
					this.configButtonWidth, this.configButtonHeight, 3,
					Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
				Draw.roundedRect(graphics, this.configButtonX + 1, this.configButtonY + 1,
					this.configButtonWidth - 2, this.configButtonHeight - 2, 2,
					Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
				graphics.centeredText(font, "Editar configuracion",
					this.configButtonX + this.configButtonWidth / 2, this.configButtonY + 4,
					Theme.withAlpha(Theme.TEXT, opacity));

				if (hovered) {
					Tooltip.request("Archivos de configuracion",
						"Abre los archivos que " + mod.displayName() + " ha creado en config/ "
							+ "para editarlos aqui mismo, con guardar y restablecer.",
						this.hoverX, this.hoverY);
				}
				cursorY += 21;
			}

			// Full description, wrapped rather than cut off at one line.
			if (!mod.description().isBlank()) {
				cursorY = Draw.wrappedText(graphics, font, mod.description(), textX, cursorY,
					maxWidth, 6, Theme.TEXT_MUTED, opacity);
				cursorY += 4;
			}

			if (known != null) {
				int badgeX = textX;
				for (ModRole role : known.roles()) {
					int badgeWidth = font.width(role.label()) + 8;
					if (badgeX + badgeWidth > textX + maxWidth) {
						break;
					}
					badgeX += Draw.badge(graphics, font, role.label(), badgeX, cursorY, roleColor(role), opacity) + 3;
				}
				cursorY += 14;
				cursorY = Draw.wrappedText(graphics, font, known.summary(), textX, cursorY,
					maxWidth, 5, Theme.TEXT_DIM, opacity);
				cursorY += 2;
				Draw.clippedText(graphics, font,
					"FPS " + signed(known.fpsImpact()) + "   Latencia " + signed(known.latencyImpact()),
					textX, cursorY, maxWidth,
					Theme.withAlpha(known.fpsImpact() >= 0 ? Theme.GOOD : Theme.DANGER, opacity), false);
				cursorY += 12;
			} else {
				Draw.clippedText(graphics, font, "Sin datos de rendimiento en el catalogo.", textX, cursorY,
					maxWidth, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
				cursorY += 12;
			}

			String mixins = mod.hasMixins()
				? mod.mixinConfigs().size() + " configuraciones de mixins"
				: "No usa mixins";
			Draw.clippedText(graphics, font, mixins, textX, cursorY, maxWidth,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
			cursorY += 14;

			Draw.divider(graphics, textX, cursorY, maxWidth, opacity);
			cursorY += 7;
		}

		// Folder-wide verdict underneath the per-mod detail.
		cursorY = Draw.sectionHeader(graphics, font,
			this.breakpoint.isCompact() ? "Tu conjunto" : "Que hacen tus mods juntos",
			textX, cursorY, maxWidth, Theme.ACCENT_BRIGHT, opacity);

		Combined combined = this.combine();
		cursorY = this.line(graphics, font, textX, cursorY, maxWidth, opacity,
			"Reconocidos: " + combined.known + " de " + this.userMods.size(), Theme.TEXT_MUTED);
		cursorY = this.line(graphics, font, textX, cursorY, maxWidth, opacity,
			"Efecto en FPS: " + signed(combined.fps), combined.fps >= 0 ? Theme.GOOD : Theme.DANGER);
		cursorY = this.line(graphics, font, textX, cursorY, maxWidth, opacity,
			"Efecto en latencia: " + signed(combined.latency), combined.latency >= 0 ? Theme.GOOD : Theme.DANGER);
		cursorY += 4;

		if (!combined.strengths.isEmpty()) {
			cursorY = this.section(graphics, font, textX, cursorY, maxWidth, opacity, "Ya cubres", combined.strengths, Theme.GOOD);
		}
		if (!combined.gaps.isEmpty()) {
			cursorY = this.section(graphics, font, textX, cursorY, maxWidth, opacity, "Te falta", combined.gaps, Theme.INFO);
		}
		if (!combined.problems.isEmpty()) {
			cursorY = this.section(graphics, font, textX, cursorY, maxWidth, opacity, "Posibles problemas", combined.problems, Theme.WARN);
		}

		graphics.disableScissor();

		int content = cursorY + this.detailScroll - this.detailY;
		this.detailMaxScroll = Math.max(0, content - this.detailHeight + 8);
		this.detailScroll = Mth.clamp(this.detailScroll, 0, this.detailMaxScroll);
	}

	private InstalledMod selectedMod() {
		return this.selected >= 0 && this.selected < this.userMods.size()
			? this.userMods.get(this.selected)
			: null;
	}

	private int section(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth,
						float opacity, String heading, List<String> items, int color) {
		int cursorY = y;
		graphics.text(font, heading, x, cursorY, Theme.withAlpha(color, opacity), false);
		cursorY += 11;
		for (String item : items) {
			Draw.clippedText(graphics, font, "- " + item, x + 4, cursorY, maxWidth - 4,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			cursorY += 10;
		}
		return cursorY + 4;
	}

	private int line(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth, float opacity,
					 String text, int color) {
		Draw.clippedText(graphics, font, text, x, y, maxWidth, Theme.withAlpha(color, opacity), false);
		return y + 11;
	}

	/** Rolls the whole mod folder up into one verdict. */
	private Combined combine() {
		Combined combined = new Combined();
		boolean hasFps = false;
		boolean hasLatency = false;
		boolean hasMemory = false;
		boolean hasTick = false;

		for (InstalledMod mod : this.userMods) {
			CatalogEntry entry = Catalog.findInstalled(mod.id());
			if (entry == null) {
				continue;
			}
			combined.known++;
			combined.fps += entry.fpsImpact();
			combined.latency += entry.latencyImpact();

			hasFps |= entry.hasRole(ModRole.FPS);
			hasLatency |= entry.hasRole(ModRole.LATENCY);
			hasMemory |= entry.hasRole(ModRole.MEMORY);
			hasTick |= entry.hasRole(ModRole.TICK);

			if (entry.hurtsLatency()) {
				combined.problems.add(entry.name() + " anade retardo a la interfaz");
			}
			if (entry.fpsImpact() < 0) {
				combined.problems.add(entry.name() + " cuesta FPS");
			}
			for (String requiredId : entry.requires()) {
				boolean present = this.userMods.stream().anyMatch(candidate -> {
					CatalogEntry required = Catalog.find(requiredId);
					return required != null && required.matches(candidate.id());
				});
				if (!present) {
					combined.problems.add(entry.name() + " necesita " + requiredId + " y no lo tienes");
				}
			}
		}

		if (hasFps) {
			combined.strengths.add("Renderizado acelerado");
		} else {
			combined.gaps.add("Un motor de render rapido (Sodium)");
		}
		if (hasLatency) {
			combined.strengths.add("Camino de entrada acortado");
		} else {
			combined.gaps.add("Entrada cruda (Raw Input Buffer / Ixeris)");
		}
		if (hasMemory) {
			combined.strengths.add("Consumo de RAM reducido");
		} else {
			combined.gaps.add("Reduccion de memoria (FerriteCore)");
		}
		if (hasTick) {
			combined.strengths.add("Logica del juego optimizada");
		} else {
			combined.gaps.add("Optimizacion de tick (Lithium)");
		}

		return combined;
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	static int roleColor(ModRole role) {
		return switch (role) {
			case FPS -> Theme.GOOD;
			case LATENCY -> Theme.INFO;
			case MEMORY, TICK -> Theme.ACCENT_BRIGHT;
			case NETWORK -> 0xFF7FD8C8;
			case VISUAL -> Theme.WARN;
			case PVP -> 0xFFFF9ED2;
			case LIBRARY -> Theme.TEXT_DIM;
		};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.configButtonVisible && Draw.inside(mouseX, mouseY,
			this.configButtonX, this.configButtonY, this.configButtonWidth, this.configButtonHeight)) {
			InstalledMod mod = this.selectedMod();
			if (mod != null) {
				this.dialogOpener.accept(new ConfigBrowserDialog(mod));
			}
			return true;
		}

		int listTop = this.listY + 20;
		if (!Draw.inside(mouseX, mouseY, this.listX, listTop, this.listWidth, this.listHeight - 22)) {
			return false;
		}
		int index = (int) ((mouseY - listTop + this.scroll) / this.rowHeight);
		if (index >= 0 && index < this.userMods.size()) {
			this.selected = index;
			// A new selection starts at the top of its detail.
			this.detailScroll = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (Draw.inside(mouseX, mouseY, this.listX, this.listY, this.listWidth, this.listHeight)) {
			if (this.maxScroll <= 0) {
				return false;
			}
			this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * this.rowHeight), 0, this.maxScroll);
			return true;
		}
		if (Draw.inside(mouseX, mouseY, this.detailX, this.detailY, this.detailWidth, this.detailHeight)) {
			if (this.detailMaxScroll <= 0) {
				return false;
			}
			this.detailScroll = Mth.clamp(this.detailScroll - (int) Math.round(amount * 16), 0, this.detailMaxScroll);
			return true;
		}
		return false;
	}

	/** Mutable accumulator for the folder-wide verdict. */
	private static final class Combined {
		private int known;
		private int fps;
		private int latency;
		private final List<String> strengths = new ArrayList<>();
		private final List<String> gaps = new ArrayList<>();
		private final List<String> problems = new ArrayList<>();
	}
}

package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.catalog.CatalogEntry;
import dev.zymekoh.optimatch.catalog.ModrinthClient;
import dev.zymekoh.optimatch.catalog.ModrinthProject;
import dev.zymekoh.optimatch.catalog.ModrinthVersion;
import dev.zymekoh.optimatch.catalog.Preset;
import dev.zymekoh.optimatch.catalog.PresetBuilder;
import dev.zymekoh.optimatch.catalog.Recommendation;
import dev.zymekoh.optimatch.hardware.HardwareProfile;
import dev.zymekoh.optimatch.hardware.HardwareScanner;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Impact;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import dev.zymekoh.optimatch.ui.dialog.InstallDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 2 — reads the machine, then offers the three one-click goals.
 *
 * <p>Nothing is presented as installable until Modrinth confirms a build exists for the Minecraft
 * version actually running. Entries with no such build move into their own section with the reason,
 * rather than being quietly dropped, so the player can see the tool checked.
 */
public final class ForYouTab implements OptiTab {
	private static final Preset[] PRESETS = Preset.values();

	private final Consumer<Dialog> dialogOpener;

	private HardwareProfile hardware;
	private List<InstalledMod> installed = List.of();
	private Recommendation active;

	/** Live compatibility verdicts, keyed by catalog mod id. Null until the lookup resolves. */
	private Map<String, ModrinthClient.Availability> availability;
	private boolean checking;

	/** Modrinth project metadata for the suggested mods, purely so the real icons can be drawn. */
	private Map<String, ModrinthProject> projectInfo = Map.of();

	/**
	 * Install-button rectangles, rebuilt every frame. Recording them during render is what keeps the
	 * hit test in step with a list that scrolls and reflows.
	 */
	private final List<Hotspot> hotspots = new ArrayList<>();

	private record Hotspot(int x, int y, int width, int height, CatalogEntry entry) {
	}

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private int x;
	private int y;
	private int width;
	private int height;
	private int analysisHeight;
	private int buttonsTop;
	private int buttonHeight;
	private int buttonWidth;
	private int buttonGap;
	private boolean buttonsStacked;
	private int resultTop;

	private int scroll;
	private int maxScroll;

	public ForYouTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

	@Override
	public Component title() {
		return Component.literal("Para ti");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Para ti");
	}

	@Override
	public void onSelected() {
		if (this.hardware == null) {
			this.hardware = HardwareScanner.profile();
			this.installed = ModScanner.scan();
		}
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;

		this.analysisHeight = Mth.clamp(Math.round(height * 0.32F), 44, breakpoint.pick(66, 76, 84));
		this.buttonsTop = y + this.analysisHeight + 6;
		this.buttonGap = breakpoint.pick(3, 6, 8);

		this.buttonsStacked = breakpoint.isCompact();
		if (this.buttonsStacked) {
			this.buttonHeight = 16;
			this.buttonWidth = width;
			this.resultTop = this.buttonsTop + (this.buttonHeight + 3) * PRESETS.length + 4;
		} else {
			this.buttonHeight = 32;
			this.buttonWidth = Math.max(60, (width - this.buttonGap * (PRESETS.length - 1)) / PRESETS.length);
			this.resultTop = this.buttonsTop + this.buttonHeight + 7;
		}
	}

	private int buttonX(int index) {
		return this.buttonsStacked ? this.x : this.x + index * (this.buttonWidth + this.buttonGap);
	}

	private int buttonY(int index) {
		return this.buttonsStacked ? this.buttonsTop + index * (this.buttonHeight + 3) : this.buttonsTop;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		this.renderAnalysis(graphics, font, opacity);
		this.renderPresetButtons(graphics, font, mouseX, mouseY, now, opacity);
		this.renderResult(graphics, font, mouseX, mouseY, now, opacity);
	}

	private void renderAnalysis(GuiGraphicsExtractor graphics, Font font, float opacity) {
		Draw.panel(graphics, this.x, this.y, this.width, this.analysisHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		if (this.hardware == null) {
			graphics.text(font, "Analizando tu equipo...", this.x + 8, this.y + 8,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			return;
		}

		graphics.enableScissor(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.analysisHeight - 1);

		int textX = this.x + 8;
		int cursorY = this.y + 6;

		int score = this.hardware.performanceScore();
		int meterWidth = this.breakpoint.pick(70, 100, 136);
		int meterX = this.x + this.width - meterWidth - 8;
		int maxTextWidth = Math.max(40, meterX - textX - 10);

		graphics.text(font, "Tu equipo — " + this.hardware.platform().label(), textX, cursorY,
			Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);
		cursorY += 11;

		String[] rows = {
			"CPU  " + this.hardware.cpuSummary(),
			"GPU  " + this.hardware.gpuSummary(),
			"RAM  " + this.hardware.ramSummary(),
			"Pantalla  " + (this.hardware.refreshRateHz() > 0 ? this.hardware.refreshRateHz() + " Hz" : "desconocida")
		};
		for (String row : rows) {
			if (cursorY > this.y + this.analysisHeight - 10) {
				break;
			}
			Draw.clippedText(graphics, font, row, textX, cursorY, maxTextWidth,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			cursorY += 10;
		}

		Draw.statBlock(graphics, font, score + "/100", "potencia", meterX, this.y + 8, meterWidth,
			score >= 65 ? Theme.GOOD : score >= 40 ? Theme.WARN : Theme.DANGER, opacity);
		Draw.bar(graphics, meterX, this.y + 30, meterWidth, 5, score / 100.0F,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity),
			Theme.withAlpha(score >= 65 ? Theme.GOOD : score >= 40 ? Theme.WARN : Theme.DANGER, opacity));

		graphics.disableScissor();
	}

	private void renderPresetButtons(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		for (int index = 0; index < PRESETS.length; index++) {
			Preset preset = PRESETS[index];
			int bx = this.buttonX(index);
			int by = this.buttonY(index);
			boolean hovered = Draw.inside(mouseX, mouseY, bx, by, this.buttonWidth, this.buttonHeight);
			boolean chosen = this.active != null && this.active.preset() == preset;

			int accent = preset.accent();
			int border = chosen || hovered ? accent : Theme.BORDER_SOFT;
			Draw.roundedRect(graphics, bx, by, this.buttonWidth, this.buttonHeight, 5,
				Theme.withAlpha(border, opacity));
			Draw.roundedRect(graphics, bx + 1, by + 1, this.buttonWidth - 2, this.buttonHeight - 2, 4,
				Theme.withAlpha(chosen ? Theme.PANEL_HOVER : hovered ? Theme.PANEL_RAISED : Theme.PANEL, opacity));

			if (chosen) {
				float pulse = Draw.wave(now, 1400L);
				graphics.fill(bx + 4, by + this.buttonHeight - 3, bx + this.buttonWidth - 4, by + this.buttonHeight - 2,
					Theme.argb(Math.round((140 + pulse * 100) * opacity), accent & 0xFFFFFF));
			}

			if (this.buttonsStacked) {
				Draw.clippedText(graphics, font, preset.title(), bx + 7, by + (this.buttonHeight - 8) / 2,
					this.buttonWidth - 14, Theme.withAlpha(Theme.TEXT, opacity), false);
			} else {
				graphics.centeredText(font, font.plainSubstrByWidth(preset.title(), this.buttonWidth - 8),
					bx + this.buttonWidth / 2, by + 7, Theme.withAlpha(Theme.TEXT, opacity));
				Draw.wrappedText(graphics, font, preset.description(), bx + 5, by + 18,
					this.buttonWidth - 10, 1, Theme.TEXT_DIM, opacity);
			}
		}
	}

	private void renderResult(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		this.hotspots.clear();

		int resultHeight = this.y + this.height - this.resultTop;
		if (resultHeight < 18) {
			return;
		}

		Draw.panel(graphics, this.x, this.resultTop, this.width, resultHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		if (this.active == null) {
			graphics.centeredText(font,
				Component.literal(this.breakpoint.isCompact()
					? "Elige un objetivo" : "Elige un objetivo arriba para ver que mods te faltan"),
				this.x + this.width / 2, this.resultTop + resultHeight / 2 - 4,
				Theme.withAlpha(Theme.TEXT_DIM, opacity));
			return;
		}

		int textX = this.x + 8;
		int maxWidth = this.width - 16;
		int bottom = this.resultTop + resultHeight - 4;

		graphics.enableScissor(this.x + 1, this.resultTop + 1, this.x + this.width - 1, bottom);
		int cursorY = this.resultTop + 7 - this.scroll;

		if (this.availability == null) {
			String message = this.checking
				? "Comprobando en Modrinth que existan para " + ModrinthClient.gameVersion() + "..."
				: "Preparando la comprobacion de compatibilidad...";
			graphics.text(font, font.plainSubstrByWidth(message, maxWidth), textX, cursorY,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			int sweepWidth = Math.min(160, maxWidth);
			Draw.roundedRect(graphics, textX, cursorY + 14, sweepWidth, 4, 2,
				Theme.withAlpha(Theme.PANEL_RAISED, opacity));
			int sweep = Math.round(Draw.cycle(now, 1300L) * Math.max(1, sweepWidth - 36));
			Draw.roundedRect(graphics, textX + sweep, cursorY + 14, 36, 4, 2,
				Theme.withAlpha(Theme.ACCENT, opacity));
			graphics.disableScissor();
			return;
		}

		List<Recommendation.Suggestion> install = this.active.install();
		int compatible = 0;
		int blocked = 0;
		for (Recommendation.Suggestion suggestion : install) {
			if (this.isInstallable(suggestion.entry())) {
				compatible++;
			} else {
				blocked++;
			}
		}

		cursorY = Draw.sectionHeader(graphics, font, "Instalar (" + compatible + ")", textX, cursorY,
			maxWidth, Theme.ACCENT_BRIGHT, opacity);
		for (Recommendation.Suggestion suggestion : install) {
			if (this.isInstallable(suggestion.entry())) {
				cursorY = this.entryRow(graphics, font, textX, cursorY, maxWidth, mouseX, mouseY, opacity,
					suggestion, bottom, true);
			}
		}
		if (compatible == 0) {
			Draw.clippedText(graphics, font, "Nada que anadir: ya tienes todo lo compatible.", textX + 4, cursorY,
				maxWidth - 4, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
			cursorY += 12;
		}

		if (blocked > 0) {
			cursorY += 4;
			cursorY = Draw.sectionHeader(graphics, font,
				"Sin version para " + ModrinthClient.gameVersion() + " (" + blocked + ")",
				textX, cursorY, maxWidth, Theme.DANGER, opacity);
			for (Recommendation.Suggestion suggestion : install) {
				CatalogEntry entry = suggestion.entry();
				if (!this.isInstallable(entry)) {
					ModrinthClient.Availability verdict = this.availability.get(entry.modId());
					String reason = verdict == null ? "sin comprobar" : verdict.detail();
					Draw.clippedText(graphics, font, entry.name() + " — " + reason, textX + 4, cursorY,
						maxWidth - 4, Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.9F), false);
					cursorY += 11;
				}
			}
		}

		if (!this.active.alreadyHave().isEmpty()) {
			cursorY += 4;
			cursorY = Draw.sectionHeader(graphics, font,
				"Ya lo tienes (" + this.active.alreadyHave().size() + ")", textX, cursorY, maxWidth,
				Theme.GOOD, opacity);
			for (Recommendation.Suggestion suggestion : this.active.alreadyHave()) {
				cursorY = this.entryRow(graphics, font, textX, cursorY, maxWidth, mouseX, mouseY, opacity,
					suggestion, bottom, false);
			}
		}

		if (!this.active.warnings().isEmpty()) {
			cursorY += 4;
			cursorY = Draw.sectionHeader(graphics, font,
				"Trabajan en tu contra (" + this.active.warnings().size() + ")", textX, cursorY, maxWidth,
				Theme.WARN, opacity);
			for (Recommendation.Warning warning : this.active.warnings()) {
				Draw.clippedText(graphics, font, warning.name() + " — " + warning.reason(),
					textX + 4, cursorY, maxWidth - 4, Theme.withAlpha(Theme.WARN, opacity * 0.9F), false);
				cursorY += 10;
			}
		}

		graphics.disableScissor();

		int contentHeight = cursorY + this.scroll - this.resultTop;
		this.maxScroll = Math.max(0, contentHeight - resultHeight + 8);
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	private boolean isInstallable(CatalogEntry entry) {
		ModrinthClient.Availability verdict = this.availability == null ? null : this.availability.get(entry.modId());
		return verdict != null && verdict.isInstallable();
	}

	/**
	 * One recommended mod: icon, name, channel badge, impact, description and — when it is not already
	 * installed — the Install button that opens the confirmation dialog.
	 */
	private int entryRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth,
						 int mouseX, int mouseY, float opacity, Recommendation.Suggestion suggestion,
						 int bottom, boolean offerInstall) {
		CatalogEntry entry = suggestion.entry();
		int rowHeight = this.breakpoint.isCompact() ? 24 : 34;
		if (y > bottom) {
			return y + rowHeight;
		}

		int icon = this.breakpoint.pick(14, 16, 18);
		int accent = InstalledModsTab.roleColor(entry.primaryRole());
		ModrinthClient.Availability iconVerdict = this.availability.get(entry.modId());
		String projectId = iconVerdict != null && iconVerdict.version() != null
			? iconVerdict.version().projectId() : "";
		if (!projectId.isBlank()) {
			ModIcons.drawRemote(graphics, font, projectId, entry.name(), x + 3, y, icon, accent, opacity);
		} else {
			ModIcons.draw(graphics, font, entry.modId(), entry.name(), x + 3, y, icon, accent, opacity);
		}

		int textX = x + 3 + icon + 6;
		int installWidth = this.breakpoint.isCompact() ? 46 : 58;
		int installX = x + maxWidth - installWidth - 2;

		// The Install button owns the right edge; text gets whatever is left.
		int textLimit = offerInstall ? installX - textX - 6 : maxWidth - (textX - x) - 4;

		Draw.clippedText(graphics, font, entry.name(), textX, y, Math.max(20, textLimit),
			Theme.withAlpha(offerInstall ? Theme.TEXT : Theme.TEXT_DIM, opacity), false);

		ModrinthClient.Availability verdict = this.availability.get(entry.modId());
		int metaY = y + 11;
		int metaX = textX;

		if (verdict != null && verdict.version() != null) {
			ModrinthVersion version = verdict.version();
			int badgeColor = switch (version.channel()) {
				case RELEASE -> Theme.GOOD;
				case BETA -> Theme.WARN;
				case ALPHA -> Theme.DANGER;
			};
			metaX += Draw.badge(graphics, font, version.channel().label(), metaX, metaY, badgeColor, opacity) + 5;
		}

		// Arrows show which way the real metric moves: FPS up is good, latency down is good.
		String fps = Impact.fpsLabel(entry.fpsImpact());
		graphics.text(font, fps, metaX, metaY, Theme.withAlpha(Impact.color(entry.fpsImpact()), opacity), false);
		metaX += font.width(fps) + 8;
		String latency = Impact.latencyLabel(entry.latencyImpact());
		if (metaX + font.width(latency) < installX - 4) {
			graphics.text(font, latency, metaX, metaY,
				Theme.withAlpha(Impact.color(entry.latencyImpact()), opacity), false);
		}

		if (!this.breakpoint.isCompact()) {
			// The hardware fact that earned this mod its place, not a generic blurb.
			Draw.clippedText(graphics, font, suggestion.reason(), textX, y + 22, Math.max(20, textLimit),
				Theme.withAlpha(Theme.INFO, opacity * 0.85F), false);
		}

		// Hovering the row explains what the mod does and why it ranked where it did.
		if (Draw.inside(mouseX, mouseY, x, y, maxWidth, rowHeight - 2)) {
			Tooltip.request(entry.name() + "  ·  relevancia " + suggestion.relevance() + "/100",
				Impact.explain(entry.fpsImpact(), entry.latencyImpact()) + "  " + entry.summary()
					+ "  —  " + suggestion.reason(), mouseX, mouseY);
		}

		if (offerInstall) {
			int buttonY = y + 2;
			int buttonHeight = 16;
			boolean hovered = Draw.inside(mouseX, mouseY, installX, buttonY, installWidth, buttonHeight);
			Draw.roundedRect(graphics, installX, buttonY, installWidth, buttonHeight, 3,
				Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.ACCENT, opacity));
			Draw.roundedRect(graphics, installX + 1, buttonY + 1, installWidth - 2, buttonHeight - 2, 2,
				Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
			graphics.centeredText(font, "Instalar", installX + installWidth / 2, buttonY + 4,
				Theme.withAlpha(Theme.TEXT, opacity));

			this.hotspots.add(new Hotspot(installX, buttonY, installWidth, buttonHeight, entry));
		}

		return y + rowHeight;
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Install buttons take priority over everything else in the results panel.
		for (Hotspot hotspot : this.hotspots) {
			if (Draw.inside(mouseX, mouseY, hotspot.x(), hotspot.y(), hotspot.width(), hotspot.height())) {
				CatalogEntry entry = hotspot.entry();
				ModrinthClient.Availability verdict = this.availability == null
					? null : this.availability.get(entry.modId());
				String projectId = verdict != null && verdict.version() != null
					? verdict.version().projectId() : "";
				this.dialogOpener.accept(new InstallDialog(entry.slug(), entry.name(), projectId));
				return true;
			}
		}

		for (int index = 0; index < PRESETS.length; index++) {
			if (Draw.inside(mouseX, mouseY, this.buttonX(index), this.buttonY(index), this.buttonWidth, this.buttonHeight)) {
				if (this.hardware != null) {
					this.active = PresetBuilder.build(PRESETS[index], this.hardware, this.installed);
					this.scroll = 0;
					this.startCompatibilityCheck();
				}
				return true;
			}
		}
		return false;
	}

	/** Asks Modrinth whether each suggested mod actually has a build for this Minecraft version. */
	private void startCompatibilityCheck() {
		this.availability = null;
		this.checking = true;
		Recommendation snapshot = this.active;

		ModrinthClient.availabilityOf(snapshot.installEntries()).thenAccept(results -> {
			// Ignore a late reply for a preset the player has already moved away from.
			if (this.active == snapshot) {
				this.availability = results;
				this.checking = false;
				this.loadIcons(results);
			}
		}).exceptionally(throwable -> {
			if (this.active == snapshot) {
				this.availability = Map.of();
				this.checking = false;
			}
			return null;
		});
	}

	/**
	 * Suggested mods are not installed, so they have no local icon. One bulk call gets the real
	 * artwork for the whole list instead of a letter tile per row.
	 */
	private void loadIcons(Map<String, ModrinthClient.Availability> results) {
		List<String> ids = new ArrayList<>();
		for (ModrinthClient.Availability verdict : results.values()) {
			if (verdict.isInstallable() && !verdict.version().projectId().isBlank()) {
				ids.add(verdict.version().projectId());
			}
		}
		if (ids.isEmpty()) {
			return;
		}
		ModrinthClient.projects(ids).thenAccept(projects -> {
			this.projectInfo = projects;
			for (ModrinthProject project : projects.values()) {
				ModIcons.requestRemote(project.projectId(), project.iconUrl());
			}
		});
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0
			|| !Draw.inside(mouseX, mouseY, this.x, this.resultTop, this.width, this.y + this.height - this.resultTop)) {
			return false;
		}
		this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * 16), 0, this.maxScroll);
		return true;
	}
}

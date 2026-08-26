package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.transform.KnobRegistry;
import dev.zymekoh.optimatch.transform.KnobStore;
import dev.zymekoh.optimatch.transform.MixinInventory;
import dev.zymekoh.optimatch.transform.MixinKnob;
import dev.zymekoh.optimatch.transform.TransformApplier;
import dev.zymekoh.optimatch.transform.TransformPlan;
import dev.zymekoh.optimatch.transform.TransformPreset;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Mascot;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Spinner;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import dev.zymekoh.optimatch.ui.dialog.TransformReviewDialog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Tab 7 — every mixin in the instance, and the ones you are allowed to move.
 *
 * <p>The census on the left of the divide is universal: walking Mixin's prepared configs lists what
 * every mod injected, cooperative or not. What you can <em>change</em> is not universal, and the tab
 * says so rather than pretending. A mod that publishes an {@code IMixinConfigPlugin} accepts being
 * switched off in pieces; one that does not is shown with its mixins and a lock, because the
 * alternative — writing changes that silently do nothing — is worse than an honest no.
 */
public final class TransformTab implements OptiTab {
	private static final int ROW_HEIGHT = 30;
	private static final int PRESET_HEIGHT = 34;

	private enum Phase {
		BROWSING,
		APPLYING,
		DONE
	}

	private final Consumer<Dialog> dialogOpener;

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private int x;
	private int y;
	private int width;
	private int height;
	private int leftWidth;
	private int listX;
	private int listWidth;
	private int listTop;
	private int listHeight;

	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	private List<MixinKnob> knobs = List.of();
	private final Map<String, Boolean> values = new LinkedHashMap<>();
	private List<MixinInventory.Config> locked = List.of();

	private TransformPreset selected;
	private TransformPlan plan;

	private Phase phase = Phase.BROWSING;
	private long phaseStartedAt;
	private CompletableFuture<TransformApplier.Result> running;
	private TransformApplier.Result result;
	private boolean restartPending;

	/** Row rectangles rebuilt every frame, so hit-testing always matches what was drawn. */
	private final List<int[]> knobHits = new ArrayList<>();

	public TransformTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

	@Override
	public Component title() {
		return Component.literal("Taller de mixins");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Taller");
	}

	@Override
	public void onSelected() {
		ModIcons.preload(ModScanner.scan());
		this.refresh();
	}

	private void refresh() {
		this.knobs = KnobRegistry.knobs();
		this.locked = KnobRegistry.locked();
		this.values.clear();
		for (MixinKnob knob : this.knobs) {
			this.values.put(knob.key(), KnobStore.read(knob));
		}
		if (this.selected != null) {
			this.plan = TransformPlan.build(this.selected);
		}
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;

		if (breakpoint.isCompact()) {
			// Too narrow for two columns: presets become a band above the list.
			this.leftWidth = width;
			this.listX = x;
			this.listWidth = width;
			this.listTop = y + PRESET_HEIGHT + 8;
		} else {
			this.leftWidth = Mth.clamp(Math.round(width * 0.38F), 150, 260);
			this.listX = x + this.leftWidth + 8;
			this.listWidth = width - this.leftWidth - 8;
			this.listTop = y;
		}
		this.listHeight = Math.max(40, y + height - this.listTop);
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		if (this.knobs.isEmpty() && this.locked.isEmpty()) {
			this.refresh();
		}

		if (this.phase == Phase.APPLYING) {
			this.renderApplying(graphics, font, now, opacity);
			return;
		}

		this.renderPresets(graphics, font, mouseX, mouseY, opacity);
		this.renderList(graphics, font, mouseX, mouseY, opacity);
	}

	// ---------------------------------------------------------------- presets

	private void renderPresets(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		if (this.breakpoint.isCompact()) {
			this.renderPresetStrip(graphics, font, mouseX, mouseY, opacity);
			return;
		}

		Draw.panel(graphics, this.x, this.y, this.leftWidth, this.height, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		int inner = this.leftWidth - 16;
		int cursorY = Draw.sectionHeader(graphics, font, "Presets", this.x + 8, this.y + 8, inner,
			Theme.ACCENT_BRIGHT, opacity);

		List<TransformPreset> presets = TransformPreset.all();
		for (int index = 0; index < presets.size(); index++) {
			TransformPreset preset = presets.get(index);
			boolean chosen = this.selected != null && this.selected.id().equals(preset.id());
			boolean hovered = Draw.inside(mouseX, mouseY, this.x + 8, cursorY, inner, PRESET_HEIGHT);

			Draw.roundedRect(graphics, this.x + 8, cursorY, inner, PRESET_HEIGHT, 4,
				Theme.withAlpha(chosen ? Theme.ACCENT : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			if (index == 0) {
				// The headline preset gets a spine so it reads as the recommended one.
				graphics.fill(this.x + 8, cursorY, this.x + 10, cursorY + PRESET_HEIGHT,
					Theme.withAlpha(Theme.GOOD, opacity));
			}

			Draw.clippedText(graphics, font, preset.name(), this.x + 14, cursorY + 5, inner - 12,
				Theme.withAlpha(chosen ? Theme.TEXT : Theme.TEXT, opacity), false);
			Draw.clippedText(graphics, font, preset.tagline(), this.x + 14, cursorY + 17, inner - 12,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

			if (hovered) {
				Tooltip.request(preset.name(), preset.detail(), mouseX, mouseY);
			}
			cursorY += PRESET_HEIGHT + 4;
		}

		cursorY += 4;
		this.renderPlanSummary(graphics, font, mouseX, mouseY, this.x + 8, cursorY, inner, opacity);
	}

	private void renderPresetStrip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		List<TransformPreset> presets = TransformPreset.all();
		int chipWidth = (this.width - (presets.size() - 1) * 4) / presets.size();
		for (int index = 0; index < presets.size(); index++) {
			TransformPreset preset = presets.get(index);
			int chipX = this.x + index * (chipWidth + 4);
			boolean chosen = this.selected != null && this.selected.id().equals(preset.id());
			boolean hovered = Draw.inside(mouseX, mouseY, chipX, this.y, chipWidth, PRESET_HEIGHT);

			Draw.roundedRect(graphics, chipX, this.y, chipWidth, PRESET_HEIGHT, 4,
				Theme.withAlpha(chosen ? Theme.ACCENT : hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			graphics.centeredText(font, font.plainSubstrByWidth(preset.name(), chipWidth - 6),
				chipX + chipWidth / 2, this.y + 12, Theme.withAlpha(Theme.TEXT, opacity));
			if (hovered) {
				Tooltip.request(preset.name(), preset.detail(), mouseX, mouseY);
			}
		}
	}

	/** What the chosen preset would do here, stated before the button that does it. */
	private void renderPlanSummary(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
								   int panelX, int panelY, int panelWidth, float opacity) {
		int bottom = this.y + this.height - 8;
		int buttonHeight = 24;
		int available = bottom - buttonHeight - 6 - panelY;
		if (available < 20) {
			return;
		}

		if (this.selected == null || this.plan == null) {
			Draw.wrappedText(graphics, font,
				"Elige un preset para ver exactamente que cambiaria en tu instalacion.",
				panelX, panelY, panelWidth, 3, Theme.TEXT_DIM, opacity);
			return;
		}

		int cursorY = panelY;
		int changes = this.plan.applicable().size();
		long blocking = this.plan.count(TransformPlan.Severity.BLOCKING);
		long serious = this.plan.count(TransformPlan.Severity.SERIOUS);

		String headline = changes == 0 ? "Sin cambios" : changes + (changes == 1 ? " cambio" : " cambios");
		graphics.text(font, headline, panelX, cursorY,
			Theme.withAlpha(changes == 0 ? Theme.TEXT_DIM : Theme.GOOD, opacity), false);
		cursorY += 12;

		if (blocking > 0) {
			cursorY += 1 + drawNote(graphics, font, blocking + " no se puede", panelX, cursorY,
				panelWidth, Theme.DANGER, opacity);
		}
		if (serious > 0) {
			cursorY += 1 + drawNote(graphics, font, serious + " a revisar", panelX, cursorY,
				panelWidth, Theme.WARN, opacity);
		}

		int listBottom = bottom - buttonHeight - 6;
		for (TransformPlan.Change change : this.plan.applicable()) {
			if (cursorY + 10 > listBottom) {
				break;
			}
			Draw.clippedText(graphics, font, "· " + change.summary(), panelX, cursorY, panelWidth,
				Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
			cursorY += 10;
		}

		this.renderApplyButton(graphics, font, mouseX, mouseY, panelX, bottom - buttonHeight,
			panelWidth, buttonHeight, opacity);
	}

	private static int drawNote(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
								int width, int color, float opacity) {
		Draw.clippedText(graphics, font, text, x, y, width, Theme.withAlpha(color, opacity), false);
		return 10;
	}

	private void renderApplyButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
								   int buttonX, int buttonY, int buttonWidth, int buttonHeight, float opacity) {
		boolean enabled = this.plan != null && !this.plan.isEmpty();
		boolean hovered = enabled && Draw.inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
		int border = !enabled ? Theme.BORDER_SOFT : hovered ? Theme.ACCENT_BRIGHT : Theme.ACCENT;

		Draw.roundedRect(graphics, buttonX, buttonY, buttonWidth, buttonHeight, 4,
			Theme.withAlpha(border, opacity * (enabled ? 1.0F : 0.5F)));
		Draw.roundedRect(graphics, buttonX + 1, buttonY + 1, buttonWidth - 2, buttonHeight - 2, 3,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
		graphics.centeredText(font, Component.literal(enabled ? "Aplicar preset" : "Nada que aplicar"),
			buttonX + buttonWidth / 2, buttonY + 8,
			Theme.withAlpha(enabled ? Theme.TEXT : Theme.TEXT_DIM, opacity));

		if (hovered) {
			Tooltip.request("Aplicar " + this.selected.name(),
				"Escribe los cambios en los archivos de configuracion de cada mod. Tienen efecto al reiniciar.",
				mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ list

	private void renderList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		Draw.panel(graphics, this.listX, this.listTop, this.listWidth, this.listHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		int headerHeight = 26;
		int censusMixins = MixinInventory.totalMixins();
		Draw.clippedText(graphics, font,
			censusMixins + " mixins · " + MixinInventory.configs().size() + " configs",
			this.listX + 8, this.listTop + 6, this.listWidth - 16,
			Theme.withAlpha(Theme.TEXT, opacity), false);
		Draw.clippedText(graphics, font,
			this.knobs.size() + " ajustables · " + this.locked.size() + " bloqueados por su mod",
			this.listX + 8, this.listTop + 16, this.listWidth - 16,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		if (this.restartPending) {
			String note = "reinicia para aplicar";
			Draw.badge(graphics, font, note,
				this.listX + this.listWidth - 8 - font.width(note) - 8, this.listTop + 10,
				Theme.WARN, opacity);
		}

		int contentTop = this.listTop + headerHeight;
		int contentBottom = this.listTop + this.listHeight - 4;
		graphics.enableScissor(this.listX + 1, contentTop, this.listX + this.listWidth - 1, contentBottom);

		this.knobHits.clear();
		int cursorY = contentTop + 4 - this.scroll.intValue();
		String currentMod = null;

		for (MixinKnob knob : this.knobs) {
			if (!knob.modId().equals(currentMod)) {
				currentMod = knob.modId();
				if (cursorY + 14 >= contentTop && cursorY <= contentBottom) {
					Draw.sectionHeader(graphics, font, currentMod, this.listX + 8, cursorY,
						this.listWidth - 16, Theme.ACCENT_BRIGHT, opacity);
				}
				cursorY += 15;
			}

			if (cursorY + ROW_HEIGHT >= contentTop && cursorY <= contentBottom) {
				this.renderKnobRow(graphics, font, knob, cursorY, mouseX, mouseY, opacity);
			}
			this.knobHits.add(new int[]{cursorY, this.knobs.indexOf(knob)});
			cursorY += ROW_HEIGHT;
		}

		cursorY = this.renderLocked(graphics, font, cursorY, contentTop, contentBottom, mouseX, mouseY, opacity);

		graphics.disableScissor();

		int content = cursorY + this.scroll.intValue() - contentTop;
		this.maxScroll = Math.max(0, content - (contentBottom - contentTop) + 8);
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));
	}

	private void renderKnobRow(GuiGraphicsExtractor graphics, Font font, MixinKnob knob, int rowY,
							   int mouseX, int mouseY, float opacity) {
		int rowX = this.listX + 8;
		int rowWidth = this.listWidth - 16;
		boolean on = this.values.getOrDefault(knob.key(), knob.defaultOn());
		boolean hovered = Draw.inside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT - 2);

		Draw.roundedRect(graphics, rowX, rowY, rowWidth, ROW_HEIGHT - 2, 4,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));

		ModIcons.draw(graphics, font, knob.modId(), knob.label(), rowX + 5, rowY + 5, 14,
			Theme.ACCENT, opacity);

		// The switch is anchored to the right edge, and every text column stops before it.
		int switchWidth = 26;
		int switchX = rowX + rowWidth - switchWidth - 6;
		this.renderSwitch(graphics, switchX, rowY + 8, switchWidth, on, opacity);

		int textX = rowX + 24;
		int textWidth = switchX - textX - 8;

		int badgeWidth = 0;
		if (knob.risk() != MixinKnob.Risk.SAFE) {
			String tag = knob.risk() == MixinKnob.Risk.CORE ? "motor" : "compartido";
			badgeWidth = Draw.badge(graphics, font, tag, textX, rowY + 5,
				knob.risk() == MixinKnob.Risk.CORE ? Theme.DANGER : Theme.WARN, opacity) + 4;
		}
		Draw.clippedText(graphics, font, knob.label(), textX + badgeWidth, rowY + 5,
			textWidth - badgeWidth, Theme.withAlpha(on ? Theme.TEXT : Theme.TEXT_DIM, opacity), false);

		String meta = knob.kind() == MixinKnob.Kind.RULE
			? knob.mixinCount() + (knob.mixinCount() == 1 ? " mixin" : " mixins")
			: "opcion del mod";
		int metaWidth = font.width(meta) + 6;
		Draw.clippedText(graphics, font, knob.description(), textX, rowY + 16,
			Math.max(10, textWidth - metaWidth), Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		graphics.text(font, meta, switchX - metaWidth + 2, rowY + 16,
			Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.75F), false);

		if (hovered) {
			Tooltip.request(knob.label(), knob.description() + "  Regla: " + knob.id(), mouseX, mouseY);
		}
	}

	private void renderSwitch(GuiGraphicsExtractor graphics, int switchX, int switchY, int switchWidth,
							  boolean on, float opacity) {
		int trackHeight = 12;
		Draw.roundedRect(graphics, switchX, switchY, switchWidth, trackHeight, trackHeight / 2,
			Theme.withAlpha(on ? Theme.GOOD : Theme.BORDER, opacity * (on ? 0.75F : 0.6F)));
		int knobSize = trackHeight - 4;
		int knobX = on ? switchX + switchWidth - knobSize - 2 : switchX + 2;
		Draw.roundedRect(graphics, knobX, switchY + 2, knobSize, knobSize, knobSize / 2,
			Theme.withAlpha(Theme.TEXT, opacity));
	}

	/** The mods that never offered a switch, listed rather than hidden. */
	private int renderLocked(GuiGraphicsExtractor graphics, Font font, int cursorY, int contentTop,
							 int contentBottom, int mouseX, int mouseY, float opacity) {
		if (this.locked.isEmpty()) {
			return cursorY;
		}

		cursorY += 6;
		if (cursorY + 14 >= contentTop && cursorY <= contentBottom) {
			Draw.sectionHeader(graphics, font, "Bloqueados por su mod", this.listX + 8, cursorY,
				this.listWidth - 16, Theme.TEXT_DIM, opacity);
		}
		cursorY += 15;

		if (cursorY + 20 >= contentTop && cursorY <= contentBottom) {
			cursorY = Draw.wrappedText(graphics, font,
				"Estos mods no publican un IMixinConfigPlugin, que es el enganche que permite apagar "
					+ "partes sueltas. Se pueden ver, no cambiar.",
				this.listX + 8, cursorY, this.listWidth - 16, 2, Theme.TEXT_DIM, opacity * 0.85F);
		} else {
			cursorY += 20;
		}
		cursorY += 2;

		for (MixinInventory.Config config : this.locked) {
			int rowX = this.listX + 8;
			int rowWidth = this.listWidth - 16;
			if (cursorY + 18 >= contentTop && cursorY <= contentBottom) {
				boolean hovered = Draw.inside(mouseX, mouseY, rowX, cursorY, rowWidth, 18);
				Draw.roundedRect(graphics, rowX, cursorY, rowWidth, 18, 3,
					Theme.argb(Math.round((hovered ? 40 : 24) * opacity), 0x1B1030));

				String owner = KnobRegistry.ownerOf(config.name());
				ModIcons.draw(graphics, font, owner, owner, rowX + 4, cursorY + 3, 12,
					Theme.TEXT_DIM, opacity * 0.8F);

				String count = config.mixins().size() + " mixins";
				int countWidth = font.width(count) + 6;
				Draw.clippedText(graphics, font, config.name(), rowX + 20, cursorY + 5,
					rowWidth - 26 - countWidth, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
				graphics.text(font, count, rowX + rowWidth - countWidth, cursorY + 5,
					Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.7F), false);

				if (hovered) {
					Tooltip.request(config.name(),
						owner + " registra " + config.mixins().size() + " mixins y no ofrece forma de "
							+ "desactivarlos por separado. La unica palanca real es quitar el mod.",
						mouseX, mouseY);
				}
			}
			cursorY += 20;
		}
		return cursorY;
	}

	// -------------------------------------------------------------- applying

	private void renderApplying(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		Draw.panel(graphics, this.x, this.y, this.width, this.height, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		float progress = Anim.progress(this.phaseStartedAt, TransformApplier.MIN_MILLIS);
		int centerX = this.x + this.width / 2;
		int ringCenterY = this.y + this.height / 2 - 10;
		int outerRadius = 34;

		// Same vocabulary as the startup screen, so this reads as the same kind of moment.
		Spinner.halo(graphics, centerX, ringCenterY, 26, now, opacity);
		Spinner.indeterminate(graphics, centerX, ringCenterY, 22, now, opacity);
		Spinner.progress(graphics, centerX, ringCenterY, outerRadius, progress, opacity * 0.9F);

		int walkWidth = 36;
		Mascot.renderWalking(graphics, centerX - walkWidth / 2, ringCenterY + 10, walkWidth, 1, now, opacity);

		int textY = ringCenterY + outerRadius + 10;
		graphics.centeredText(font, Component.literal("Escribiendo los cambios..."),
			centerX, textY, Theme.withAlpha(Theme.TEXT, opacity));
		graphics.centeredText(font,
			Component.literal(this.selected == null ? "" : this.selected.name()),
			centerX, textY + 12, Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));

		if (this.running != null && this.running.isDone() && progress >= 1.0F) {
			this.result = this.running.join();
			this.running = null;
			this.restartPending = this.result.ok() && this.result.total() > 0;
			this.phase = Phase.DONE;
			this.refresh();
			this.dialogOpener.accept(TransformReviewDialog.outcome(this.selected, this.result));
			this.phase = Phase.BROWSING;
		}
	}

	private void startApply() {
		if (this.plan == null || this.plan.isEmpty()) {
			return;
		}
		this.phase = Phase.APPLYING;
		this.phaseStartedAt = Util.getMillis();
		this.running = TransformApplier.apply(this.plan);
	}

	// ----------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.phase == Phase.APPLYING) {
			return true;
		}

		if (this.clickedPreset(mouseX, mouseY)) {
			return true;
		}

		if (!this.breakpoint.isCompact() && this.plan != null && !this.plan.isEmpty()) {
			int inner = this.leftWidth - 16;
			int buttonY = this.y + this.height - 8 - 24;
			if (Draw.inside(mouseX, mouseY, this.x + 8, buttonY, inner, 24)) {
				this.confirmThenApply();
				return true;
			}
		}

		return this.clickedKnob(mouseX, mouseY);
	}

	private boolean clickedPreset(double mouseX, double mouseY) {
		List<TransformPreset> presets = TransformPreset.all();
		for (int index = 0; index < presets.size(); index++) {
			boolean hit;
			if (this.breakpoint.isCompact()) {
				int chipWidth = (this.width - (presets.size() - 1) * 4) / presets.size();
				hit = Draw.inside(mouseX, mouseY, this.x + index * (chipWidth + 4), this.y,
					chipWidth, PRESET_HEIGHT);
			} else {
				int rowY = this.y + 8 + 13 + index * (PRESET_HEIGHT + 4);
				hit = Draw.inside(mouseX, mouseY, this.x + 8, rowY, this.leftWidth - 16, PRESET_HEIGHT);
			}
			if (hit) {
				this.selected = presets.get(index);
				this.plan = TransformPlan.build(this.selected);
				if (this.breakpoint.isCompact()) {
					// No room for an inline summary here, so the review doubles as the preview.
					this.dialogOpener.accept(
						TransformReviewDialog.preview(this.plan, this::startApply));
				}
				return true;
			}
		}
		return false;
	}

	private boolean clickedKnob(double mouseX, double mouseY) {
		int rowX = this.listX + 8;
		int rowWidth = this.listWidth - 16;
		for (int[] hit : this.knobHits) {
			if (!Draw.inside(mouseX, mouseY, rowX, hit[0], rowWidth, ROW_HEIGHT - 2)) {
				continue;
			}
			MixinKnob knob = this.knobs.get(hit[1]);
			boolean current = this.values.getOrDefault(knob.key(), knob.defaultOn());
			TransformPreset single = new TransformPreset("manual", knob.label(),
				"Cambio suelto", knob.description(),
				Map.of(knob.key(), !current), false);
			this.selected = single;
			this.plan = TransformPlan.build(single);
			this.confirmThenApply();
			return true;
		}
		return false;
	}

	/** A modal appears only when there is something serious to say; otherwise it just runs. */
	private void confirmThenApply() {
		if (this.plan == null || this.plan.isEmpty()) {
			return;
		}
		if (this.plan.needsReview()) {
			this.dialogOpener.accept(TransformReviewDialog.review(this.plan, this::startApply));
		} else {
			this.startApply();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.phase == Phase.APPLYING || this.maxScroll <= 0
			|| !Draw.inside(mouseX, mouseY, this.listX, this.listTop, this.listWidth, this.listHeight)) {
			return false;
		}
		this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * 20, 0, this.maxScroll));
		return true;
	}
}

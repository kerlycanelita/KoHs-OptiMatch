package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.scan.Conflict;
import dev.zymekoh.optimatch.scan.ConflictAnalyzer;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.MixinScanner;
import dev.zymekoh.optimatch.scan.MixinTarget;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.scan.ModScanner;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 3 — which mods are racing each other over the same Minecraft methods, split into the ones
 * worth acting on and the ones that only look alarming.
 */
public final class ConflictsTab implements OptiTab {
	private CompletableFuture<List<Conflict>> scan;
	private List<Conflict> conflicts;
	private int injectionCount;

	private int x;
	private int y;
	private int width;
	private int height;
	private int scroll;
	private int maxScroll;
	private Breakpoint breakpoint = Breakpoint.REGULAR;

	@Override
	public Component title() {
		return Component.literal("Conflictos");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Choques");
	}

	@Override
	public void onSelected() {
		ModIcons.preload(ModScanner.scan());
		if (this.scan != null) {
			return;
		}
		// Reading every mixin class out of every jar is heavy, so it never runs on the render thread.
		this.scan = CompletableFuture.supplyAsync(() -> {
			List<InstalledMod> mods = ModScanner.scan();
			List<MixinTarget> targets = MixinScanner.scan(mods);
			this.injectionCount = targets.size();
			return ConflictAnalyzer.analyze(targets);
		}).exceptionally(throwable -> {
			OptiMatchClient.LOGGER.error("Conflict scan failed", throwable);
			return List.of();
		});
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		Draw.panel(graphics, this.x, this.y, this.width, this.height, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		if (this.conflicts == null) {
			if (this.scan != null && this.scan.isDone()) {
				this.conflicts = this.scan.join();
			} else {
				this.renderScanning(graphics, font, now, opacity);
				return;
			}
		}

		if (this.conflicts.isEmpty()) {
			graphics.centeredText(font, Component.literal("Ningun mod se pelea por los mismos metodos"),
				this.x + this.width / 2, this.y + this.height / 2 - 8, Theme.withAlpha(Theme.GOOD, opacity));
			graphics.centeredText(font, Component.literal(this.injectionCount + " inyecciones analizadas"),
				this.x + this.width / 2, this.y + this.height / 2 + 4, Theme.withAlpha(Theme.TEXT_DIM, opacity));
			return;
		}

		this.renderList(graphics, font, opacity);
	}

	private void renderScanning(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		int centerX = this.x + this.width / 2;
		int centerY = this.y + this.height / 2;
		graphics.centeredText(font, Component.literal("Leyendo los mixins de tus mods..."),
			centerX, centerY - 10, Theme.withAlpha(Theme.TEXT_MUTED, opacity));

		// Indeterminate sweep, since the work length is unknown until the jars are open.
		int barWidth = Math.min(180, this.width - 40);
		int barX = centerX - barWidth / 2;
		Draw.roundedRect(graphics, barX, centerY + 4, barWidth, 4, 2, Theme.withAlpha(Theme.PANEL_RAISED, opacity));
		int sweep = Math.round(Draw.cycle(now, 1400L) * (barWidth - 40));
		Draw.roundedRect(graphics, barX + sweep, centerY + 4, 40, 4, 2, Theme.withAlpha(Theme.ACCENT, opacity));
	}

	private void renderList(GuiGraphicsExtractor graphics, Font font, float opacity) {
		int textX = this.x + 8;
		int maxWidth = this.width - 16;

		int critical = (int) this.conflicts.stream().filter(c -> c.level() == Conflict.Level.CRITICAL).count();
		int warning = (int) this.conflicts.stream().filter(c -> c.level() == Conflict.Level.WARNING).count();
		int safe = this.conflicts.size() - critical - warning;

		if (this.breakpoint.isCompact()) {
			// No room for three labelled counters: collapse to a compact tally.
			String tally = critical + " graves  " + warning + " vigilar  " + safe + " ok";
			Draw.clippedText(graphics, font, tally, textX, this.y + 7, maxWidth,
				Theme.withAlpha(critical > 0 ? Theme.DANGER : Theme.TEXT_MUTED, opacity), false);
		} else {
			graphics.text(font, critical + " preocupantes", textX, this.y + 7,
				Theme.withAlpha(critical > 0 ? Theme.DANGER : Theme.TEXT_DIM, opacity), false);
			graphics.text(font, warning + " a vigilar", textX + 100, this.y + 7,
				Theme.withAlpha(warning > 0 ? Theme.WARN : Theme.TEXT_DIM, opacity), false);
			graphics.text(font, safe + " sin problema", textX + 190, this.y + 7,
				Theme.withAlpha(Theme.GOOD, opacity), false);
		}

		int listTop = this.y + 20;
		int listBottom = this.y + this.height - 2;
		graphics.enableScissor(this.x + 1, listTop, this.x + this.width - 1, listBottom);

		int cursorY = listTop - this.scroll;
		for (Conflict conflict : this.conflicts) {
			int cardHeight = 26 + conflict.participants().size() * 9;

			if (cursorY + cardHeight >= listTop && cursorY <= listBottom) {
				int accent = levelColor(conflict.level());
				Draw.roundedRect(graphics, textX, cursorY, maxWidth, cardHeight - 3, 4,
					Theme.withAlpha(Theme.PANEL_RAISED, opacity));
				graphics.fill(textX, cursorY, textX + 2, cursorY + cardHeight - 3, Theme.withAlpha(accent, opacity));

				Draw.clippedText(graphics, font, conflict.targetLabel(), textX + 7, cursorY + 4,
					maxWidth - 80, Theme.withAlpha(Theme.TEXT, opacity), false);
				String badge = conflict.level().label();
				graphics.text(font, badge, textX + maxWidth - font.width(badge) - 6, cursorY + 4,
					Theme.withAlpha(accent, opacity), false);

				int lineY = cursorY + 15;
				for (String participant : conflict.participants()) {
					Draw.clippedText(graphics, font, participant, textX + 11, lineY, maxWidth - 16,
						Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
					lineY += 9;
				}
				Draw.clippedText(graphics, font, conflict.advice(), textX + 7, lineY,
					maxWidth - 12, Theme.withAlpha(Theme.TEXT_MUTED, opacity * 0.9F), false);
			}

			cursorY += cardHeight + 5;
		}

		graphics.disableScissor();

		int contentHeight = cursorY + this.scroll - listTop;
		this.maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	private static int levelColor(Conflict.Level level) {
		return switch (level) {
			case CRITICAL -> Theme.DANGER;
			case WARNING -> Theme.WARN;
			case SAFE -> Theme.GOOD;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0 || !Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.height)) {
			return false;
		}
		this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * 18), 0, this.maxScroll);
		return true;
	}
}

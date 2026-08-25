package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.scan.Conflict;
import dev.zymekoh.optimatch.scan.ConflictAnalyzer;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.MixinScanner;
import dev.zymekoh.optimatch.scan.MixinTarget;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.VersusBanner;
import dev.zymekoh.optimatch.ui.dialog.ConflictDetailDialog;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 4 — which mods are racing each other over the same Minecraft methods.
 *
 * <p>A real two-mod collision is drawn as a head-to-head: both icons with a VS between them. Methods
 * that several mods merely add code to get the plain treatment, because dressing that up as a fight
 * would train the player to ignore the tab.
 */
public final class ConflictsTab implements OptiTab {
	private final Consumer<Dialog> dialogOpener;

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

	/** Row rectangles, rebuilt each frame so clicks land on what is actually drawn. */
	private final List<Hotspot> hotspots = new ArrayList<>();

	private record Hotspot(int x, int y, int width, int height, Conflict conflict) {
	}

	public ConflictsTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

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

			Map<String, String> names = new HashMap<>();
			for (InstalledMod mod : mods) {
				names.put(mod.id(), mod.displayName());
			}
			return ConflictAnalyzer.analyze(targets, names);
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

		this.renderList(graphics, font, mouseX, mouseY, now, opacity);
	}

	private void renderScanning(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		int centerX = this.x + this.width / 2;
		int centerY = this.y + this.height / 2;
		graphics.centeredText(font, Component.literal("Leyendo los mixins de tus mods..."),
			centerX, centerY - 10, Theme.withAlpha(Theme.TEXT_MUTED, opacity));

		int barWidth = Math.min(180, this.width - 40);
		int barX = centerX - barWidth / 2;
		Draw.roundedRect(graphics, barX, centerY + 4, barWidth, 4, 2, Theme.withAlpha(Theme.PANEL_RAISED, opacity));
		int sweep = Math.round(Draw.cycle(now, 1400L) * (barWidth - 40));
		Draw.roundedRect(graphics, barX + sweep, centerY + 4, 40, 4, 2, Theme.withAlpha(Theme.ACCENT, opacity));
	}

	private void renderList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		this.hotspots.clear();

		int textX = this.x + 8;
		int maxWidth = this.width - 16;

		int critical = (int) this.conflicts.stream().filter(c -> c.level() == Conflict.Level.CRITICAL).count();
		int warning = (int) this.conflicts.stream().filter(c -> c.level() == Conflict.Level.WARNING).count();
		int safe = this.conflicts.size() - critical - warning;

		if (this.breakpoint.isCompact()) {
			String tally = critical + " graves  " + warning + " vigilar  " + safe + " ok";
			Draw.clippedText(graphics, font, tally, textX, this.y + 7, maxWidth,
				Theme.withAlpha(critical > 0 ? Theme.DANGER : Theme.TEXT_MUTED, opacity), false);
		} else {
			// Flowing offsets instead of fixed ones: the counters keep their spacing at any width.
			int counterX = textX;
			counterX += drawCounter(graphics, font, critical + " preocupantes", counterX, this.y + 7,
				critical > 0 ? Theme.DANGER : Theme.TEXT_DIM, opacity);
			counterX += drawCounter(graphics, font, warning + " a vigilar", counterX, this.y + 7,
				warning > 0 ? Theme.WARN : Theme.TEXT_DIM, opacity);
			drawCounter(graphics, font, safe + " sin problema", counterX, this.y + 7, Theme.GOOD, opacity);
		}

		int listTop = this.y + 20;
		int listBottom = this.y + this.height - 2;
		graphics.enableScissor(this.x + 1, listTop, this.x + this.width - 1, listBottom);

		int cursorY = listTop - this.scroll;
		for (Conflict conflict : this.conflicts) {
			boolean duel = conflict.isDuel();
			int cardHeight = duel ? 42 : 26 + conflict.contenders().size() * 9;

			if (cursorY + cardHeight >= listTop && cursorY <= listBottom) {
				boolean hovered = Draw.inside(mouseX, mouseY, textX, cursorY, maxWidth, cardHeight - 3);
				int accent = levelColor(conflict.level());

				Draw.roundedRect(graphics, textX, cursorY, maxWidth, cardHeight - 3, 4,
					Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
				graphics.fill(textX, cursorY, textX + 2, cursorY + cardHeight - 3, Theme.withAlpha(accent, opacity));

				if (duel) {
					this.renderDuelRow(graphics, font, conflict, textX, cursorY, maxWidth, now, opacity);
				} else {
					this.renderPlainRow(graphics, font, conflict, textX, cursorY, maxWidth, accent, opacity);
				}

				if (hovered) {
					Tooltip.request(conflict.targetLabel(),
						conflict.explanation() + "  Pulsa para ver el detalle completo.", mouseX, mouseY);
				}
				this.hotspots.add(new Hotspot(textX, cursorY, maxWidth, cardHeight - 3, conflict));
			}
			cursorY += cardHeight + 5;
		}

		graphics.disableScissor();

		int contentHeight = cursorY + this.scroll - listTop;
		this.maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	/** Two mods genuinely colliding: icon, VS, icon, plus who is predicted to win. */
	private void renderDuelRow(GuiGraphicsExtractor graphics, Font font, Conflict conflict,
							   int x, int y, int maxWidth, long now, float opacity) {
		Conflict.Contender left = conflict.contenders().get(0);
		Conflict.Contender right = conflict.contenders().get(1);

		int iconSize = 16;
		int versusWidth = VersusBanner.inlineWidth(iconSize);
		VersusBanner.renderInline(graphics, font, x + 7, y + 5, iconSize, left, right, now, opacity);

		int textX = x + 7 + versusWidth + 8;
		int textWidth = Math.max(30, maxWidth - (textX - x) - 70);

		Draw.clippedText(graphics, font, conflict.targetLabel(), textX, y + 4, textWidth,
			Theme.withAlpha(Theme.TEXT, opacity), false);
		Draw.clippedText(graphics, font, left.kind().label() + "  contra  " + right.kind().label(),
			textX, y + 14, textWidth, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		// The verdict is the point of the row, so it gets its own line in colour.
		String verdict;
		int verdictColor;
		if (conflict.isTie()) {
			verdict = "Empate de prioridad: ganador impredecible";
			verdictColor = Theme.DANGER;
		} else {
			Conflict.Contender winner = conflict.predictedWinner();
			verdict = winner != null ? "Gana " + winner.displayName() : "Conviven sin pisarse";
			verdictColor = winner != null ? Theme.WARN : Theme.GOOD;
		}
		Draw.clippedText(graphics, font, verdict, textX, y + 24, textWidth,
			Theme.withAlpha(verdictColor, opacity), false);

		String badge = conflict.level().label();
		graphics.text(font, badge, x + maxWidth - font.width(badge) - 6, y + 4,
			Theme.withAlpha(levelColor(conflict.level()), opacity), false);
	}

	/** Three or more mods, or a purely additive overlap: the plain list. */
	private void renderPlainRow(GuiGraphicsExtractor graphics, Font font, Conflict conflict,
								int x, int y, int maxWidth, int accent, float opacity) {
		Draw.clippedText(graphics, font, conflict.targetLabel(), x + 7, y + 4, maxWidth - 80,
			Theme.withAlpha(Theme.TEXT, opacity), false);
		String badge = conflict.level().label();
		graphics.text(font, badge, x + maxWidth - font.width(badge) - 6, y + 4,
			Theme.withAlpha(accent, opacity), false);

		int lineY = y + 15;
		for (Conflict.Contender contender : conflict.contenders()) {
			Draw.clippedText(graphics, font,
				contender.displayName() + "  " + contender.kind().label() + "  (prioridad " + contender.priority() + ")",
				x + 11, lineY, maxWidth - 16, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
			lineY += 9;
		}
	}

	/** Draws one counter and reports how much horizontal room it took, gap included. */
	private static int drawCounter(GuiGraphicsExtractor graphics, Font font, String text, int x, int y,
								   int color, float opacity) {
		graphics.text(font, text, x, y, Theme.withAlpha(color, opacity), false);
		return font.width(text) + 14;
	}

	private static int levelColor(Conflict.Level level) {
		return switch (level) {
			case CRITICAL -> Theme.DANGER;
			case WARNING -> Theme.WARN;
			case SAFE -> Theme.GOOD;
		};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Hotspot hotspot : this.hotspots) {
			if (Draw.inside(mouseX, mouseY, hotspot.x(), hotspot.y(), hotspot.width(), hotspot.height())) {
				this.dialogOpener.accept(new ConflictDetailDialog(hotspot.conflict()));
				return true;
			}
		}
		return false;
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

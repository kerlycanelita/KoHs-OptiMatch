package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.scan.HealthCheck;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Spinner;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 6 — what is quietly wrong with the instance.
 *
 * <p>Fills the gap the other tabs leave: they describe what you have and what you could add, but not
 * what is already broken. The headline finding is dead mixins — injections aiming at methods that no
 * longer exist, which fail without any error and leave a mod doing less than it promises.
 */
public final class HealthTab implements OptiTab {
	private CompletableFuture<HealthCheck.Report> scan;
	private HealthCheck.Report report;

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private int x;
	private int y;
	private int width;
	private int height;
	private int summaryHeight;
	private int listTop;

	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	@Override
	public Component title() {
		return Component.literal("Diagnostico");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Salud");
	}

	@Override
	public void onSelected() {
		ModIcons.preload(ModScanner.scan());
		if (this.scan != null) {
			return;
		}
		// Opens jars and queries Modrinth, so it never touches the render thread.
		this.scan = CompletableFuture.supplyAsync(() -> HealthCheck.run(true))
			.exceptionally(throwable -> {
				OptiMatchClient.LOGGER.error("Health check failed", throwable);
				return new HealthCheck.Report(java.util.List.of(), 0, 0, false);
			});
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;
		this.summaryHeight = breakpoint.isCompact() ? 34 : 42;
		this.listTop = y + this.summaryHeight + 6;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		if (this.report == null) {
			if (this.scan != null && this.scan.isDone()) {
				this.report = this.scan.join();
			} else {
				this.renderScanning(graphics, font, now, opacity);
				return;
			}
		}

		this.renderSummary(graphics, font, opacity);
		this.renderFindings(graphics, font, mouseX, mouseY, opacity);
	}

	private void renderScanning(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		Draw.panel(graphics, this.x, this.y, this.width, this.height, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		int centerX = this.x + this.width / 2;
		int centerY = this.y + this.height / 2;
		Spinner.indeterminate(graphics, centerX, centerY - 12, 14, now, opacity);
		graphics.centeredText(font, Component.literal("Revisando tu instalacion..."),
			centerX, centerY + 8, Theme.withAlpha(Theme.TEXT_MUTED, opacity));
		graphics.centeredText(font, Component.literal("Comprobando mixins, memoria y actualizaciones"),
			centerX, centerY + 20, Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.8F));
	}

	/** The verdict line: one glance should say whether anything needs attention. */
	private void renderSummary(GuiGraphicsExtractor graphics, Font font, float opacity) {
		long critical = this.report.count(HealthCheck.Severity.CRITICAL);
		long warning = this.report.count(HealthCheck.Severity.WARNING);
		long info = this.report.count(HealthCheck.Severity.INFO);

		int accent = critical > 0 ? Theme.DANGER : warning > 0 ? Theme.WARN : Theme.GOOD;
		Draw.panel(graphics, this.x, this.y, this.width, this.summaryHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(accent, opacity));

		String verdict = critical > 0 ? "Hay algo que arreglar"
			: warning > 0 ? "Funciona, con reservas"
			: "Instalacion sana";
		graphics.text(font, verdict, this.x + 10, this.y + 8, Theme.withAlpha(accent, opacity), true);

		String scope = this.report.modsChecked() + " mods · "
			+ this.report.injectionsChecked() + " inyecciones revisadas"
			+ (this.report.updatesChecked() ? " · actualizaciones comprobadas" : "");
		Draw.clippedText(graphics, font, scope, this.x + 10, this.y + 20, this.width - 130,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		// Counter pills, right-aligned and flowing right to left so they never collide with the text.
		int pillX = this.x + this.width - 8;
		pillX -= pill(graphics, font, info + " info", pillX, this.y + 9, Theme.INFO, opacity);
		pillX -= pill(graphics, font, warning + " avisos", pillX, this.y + 9, Theme.WARN, opacity);
		pill(graphics, font, critical + " graves", pillX, this.y + 9, Theme.DANGER, opacity);
	}

	/** Draws a right-anchored pill and returns the width it consumed, gap included. */
	private static int pill(GuiGraphicsExtractor graphics, Font font, String text, int rightEdge, int y,
							int color, float opacity) {
		int width = font.width(text) + 8;
		Draw.roundedRect(graphics, rightEdge - width, y - 1, width, 11, 3,
			Theme.argb(Math.round(55 * opacity), color & 0xFFFFFF));
		graphics.text(font, text, rightEdge - width + 4, y + 1, Theme.withAlpha(color, opacity), false);
		return width + 5;
	}

	private void renderFindings(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		int listHeight = Math.max(20, this.y + this.height - this.listTop);
		Draw.panel(graphics, this.x, this.listTop, this.width, listHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		int textX = this.x + 8;
		int maxWidth = this.width - 16;
		int bottom = this.listTop + listHeight - 4;

		graphics.enableScissor(this.x + 1, this.listTop + 1, this.x + this.width - 1, bottom);
		int cursorY = this.listTop + 6 - this.scroll.intValue();

		for (HealthCheck.Finding finding : this.report.findings()) {
			int color = severityColor(finding.severity());
			boolean hasAction = !finding.action().isBlank();

			// Height is measured from the text that will actually be drawn, so cards never overlap.
			int detailLines = this.breakpoint.isCompact() ? 2 : 3;
			int cardHeight = 16 + detailLines * 10 + (hasAction ? 12 : 0);

			if (cursorY + cardHeight >= this.listTop && cursorY <= bottom) {
				boolean hovered = Draw.inside(mouseX, mouseY, textX, cursorY, maxWidth, cardHeight - 4);
				Draw.roundedRect(graphics, textX, cursorY, maxWidth, cardHeight - 4, 4,
					Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
				graphics.fill(textX, cursorY, textX + 2, cursorY + cardHeight - 4, Theme.withAlpha(color, opacity));

				int contentX = textX + 8;
				int contentWidth = maxWidth - 16;

				// The mod's own icon anchors the finding to something recognisable.
				if (!finding.modId().isBlank()) {
					ModIcons.draw(graphics, font, finding.modId(), finding.title(),
						contentX, cursorY + 4, 12, color, opacity);
					contentX += 17;
					contentWidth -= 17;
				}

				Draw.clippedText(graphics, font, finding.title(), contentX, cursorY + 5, contentWidth,
					Theme.withAlpha(color, opacity), false);

				int detailY = Draw.wrappedText(graphics, font, finding.detail(), contentX, cursorY + 16,
					contentWidth, detailLines, Theme.TEXT_MUTED, opacity);

				if (hasAction) {
					Draw.clippedText(graphics, font, "→ " + finding.action(), contentX, detailY,
						contentWidth, Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);
				}

				if (hovered) {
					Tooltip.request(finding.title(),
						finding.detail() + (hasAction ? "  " + finding.action() : ""), mouseX, mouseY);
				}
			}
			cursorY += cardHeight;
		}

		graphics.disableScissor();

		int content = cursorY + this.scroll.intValue() - this.listTop;
		this.maxScroll = Math.max(0, content - listHeight + 10);
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));
	}

	private static int severityColor(HealthCheck.Severity severity) {
		return switch (severity) {
			case CRITICAL -> Theme.DANGER;
			case WARNING -> Theme.WARN;
			case INFO -> Theme.INFO;
			case GOOD -> Theme.GOOD;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0 || !Draw.inside(mouseX, mouseY, this.x, this.listTop, this.width,
			this.y + this.height - this.listTop)) {
			return false;
		}
		this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * 20, 0, this.maxScroll));
		return true;
	}
}

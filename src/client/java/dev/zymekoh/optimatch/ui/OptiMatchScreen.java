package dev.zymekoh.optimatch.ui;

import dev.zymekoh.optimatch.install.PendingChanges;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import dev.zymekoh.optimatch.ui.tab.ConflictsTab;
import dev.zymekoh.optimatch.ui.tab.ForYouTab;
import dev.zymekoh.optimatch.ui.tab.InstalledModsTab;
import dev.zymekoh.optimatch.ui.tab.ModsSearchTab;
import dev.zymekoh.optimatch.ui.tab.ProfilesTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The window that greets the player before the vanilla main menu: a five-tab console over a field
 * of drifting motes, with the whole panel easing in on open.
 *
 * <p>Everything is drawn on a {@link UiScale} canvas rather than at the player's GUI scale, so the
 * layout has the same room at scale 4 as at scale 2 instead of being squeezed into 480x270.
 */
public final class OptiMatchScreen extends Screen {
	private static final long INTRO_MILLIS = 620L;
	private static final int LIFT_PIXELS = 26;

	private final Screen parent;
	private final OptiTab[] tabs;
	private ParticleField particles;
	private Dialog dialog;

	private UiScale ui = UiScale.of(1, 1);
	private Breakpoint breakpoint = Breakpoint.REGULAR;

	private long openedAt;
	private int activeTab;
	private boolean confirmingQuit;
	/** When the current tab was selected, for the content cross-fade. */
	private long tabChangedAt;

	private int frameX;
	private int frameY;
	private int frameWidth;
	private int frameHeight;
	private int headerHeight;
	private int footerHeight;
	private int pendingBarHeight;
	private int tabRowY;
	private int tabHeight;
	private int tabGap;
	private int padding;
	private int continueX;
	private int continueY;
	private int continueWidth;
	private int continueHeight;
	private int quitX;
	private int quitWidth;

	public OptiMatchScreen(Screen parent) {
		super(Component.literal("KoHs OptiMatch"));
		this.parent = parent;
		this.tabs = new OptiTab[]{
			new InstalledModsTab(this::openDialog),
			new ForYouTab(this::openDialog),
			new ModsSearchTab(this::openDialog),
			new ConflictsTab(this::openDialog),
			new ProfilesTab(this::openDialog)
		};
	}

	@Override
	protected void init() {
		if (this.openedAt == 0L) {
			this.openedAt = Util.getMillis();
			this.tabChangedAt = this.openedAt;
			this.tabs[this.activeTab].onSelected();
		}

		this.ui = UiScale.of(this.width, this.height);
		int canvasWidth = this.ui.width();
		int canvasHeight = this.ui.height();

		this.breakpoint = Breakpoint.of(canvasWidth);
		this.particles = ParticleField.forViewport(canvasWidth, canvasHeight);

		int horizontalMargin = this.breakpoint.pick(6, 24, 52);
		int verticalMargin = canvasHeight < 300 ? 8 : canvasHeight < 460 ? 18 : 30;
		this.padding = this.breakpoint.pick(8, 14, 18);

		this.frameX = horizontalMargin;
		this.frameY = verticalMargin;
		this.frameWidth = Math.max(180, canvasWidth - horizontalMargin * 2);
		this.frameHeight = Math.max(140, canvasHeight - verticalMargin * 2);

		// Roomier header: brand on its own line, then a taller tab strip.
		this.tabHeight = 20;
		this.tabGap = this.breakpoint.pick(3, 5, 6);
		this.tabRowY = 30;
		this.headerHeight = this.tabRowY + this.tabHeight + 10;
		this.footerHeight = 38;
		this.pendingBarHeight = PendingChanges.isEmpty() ? 0 : 24;

		this.continueHeight = 22;
		this.continueWidth = Math.min(220, Math.max(100, this.frameWidth / this.breakpoint.pick(2, 4, 5)));
		this.continueX = this.frameX + this.frameWidth - this.continueWidth - this.padding;
		this.continueY = this.frameY + this.frameHeight - this.footerHeight
			+ (this.footerHeight - this.continueHeight) / 2;

		this.quitWidth = Math.min(190, Math.max(90, this.frameWidth / 4));
		this.quitX = this.frameX + this.frameWidth - this.quitWidth - this.padding;

		int bodyX = this.frameX + this.padding;
		int bodyY = this.frameY + this.headerHeight;
		int bodyWidth = Math.max(80, this.frameWidth - this.padding * 2);
		int bodyHeight = Math.max(40, this.frameHeight - this.headerHeight - this.footerHeight - this.pendingBarHeight);
		for (OptiTab tab : this.tabs) {
			tab.layout(bodyX, bodyY, bodyWidth, bodyHeight, this.breakpoint);
		}

		if (this.dialog != null) {
			this.dialog.layout(canvasWidth, canvasHeight);
		}
	}

	private float introProgress() {
		return Draw.easeOut((Util.getMillis() - this.openedAt) / (float) INTRO_MILLIS);
	}

	private int liftOffset() {
		return Math.round((1.0F - introProgress()) * LIFT_PIXELS);
	}

	private int tabWidth() {
		int available = this.frameWidth - this.padding * 2;
		return Math.max(34, (available - this.tabGap * (this.tabs.length - 1)) / this.tabs.length);
	}

	private int tabX(int index) {
		return this.frameX + this.padding + index * (this.tabWidth() + this.tabGap);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
		float progress = introProgress();
		graphics.fillGradient(0, 0, this.width, this.height,
			Theme.withAlpha(Theme.BG_TOP, progress), Theme.withAlpha(Theme.BG_BOTTOM, progress));
		graphics.fillGradient(0, 0, this.width, Math.max(1, this.height / 2),
			Theme.withAlpha(Theme.GLOW_TOP, progress * 0.9F), Theme.GLOW_FADE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
		long now = Util.getMillis();
		float progress = introProgress();

		int virtualMouseX = this.ui.toVirtual(mouseX);
		int virtualMouseY = this.ui.toVirtual(mouseY);
		int lift = this.liftOffset();
		int top = this.frameY + lift;

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.ui.factor());

		this.particles.render(graphics, this.ui.width(), this.ui.height(), now, progress);

		Draw.panel(graphics, this.frameX, top, this.frameWidth, this.frameHeight, 9,
			Theme.withAlpha(Theme.PANEL, progress), Theme.withAlpha(Theme.BORDER, progress));

		// enableScissor applies the current pose, so virtual coordinates are correct here.
		graphics.enableScissor(this.frameX, top, this.frameX + this.frameWidth, top + this.frameHeight);
		this.drawHeader(graphics, virtualMouseX, virtualMouseY - lift, now, progress, top);

		// Freshly selected tabs fade and slide in, so switching reads as movement rather than a cut.
		float tabFade = Anim.easeOut(Anim.progress(this.tabChangedAt, 180L));
		int tabSlide = Math.round((1.0F - tabFade) * 8.0F);
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0F, tabSlide);
		this.tabs[this.activeTab].render(graphics, this.font, virtualMouseX, virtualMouseY - lift - tabSlide,
			now, progress * tabFade);
		graphics.pose().popMatrix();

		this.drawPendingBar(graphics, virtualMouseX, virtualMouseY - lift, progress, top);
		this.drawFooter(graphics, virtualMouseX, virtualMouseY - lift, progress, top);
		graphics.disableScissor();

		if (this.dialog != null) {
			this.dialog.render(graphics, this.font, virtualMouseX, virtualMouseY, now, progress);
		}

		// Tooltips paint last so they sit above every panel, still inside the canvas transform.
		Tooltip.renderPending(graphics, this.font, this.ui.width(), this.ui.height(), progress);

		graphics.pose().popMatrix();

		super.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now, float progress, int frameTop) {
		int textX = this.frameX + this.padding;

		graphics.text(this.font, "KoHs OptiMatch", textX, frameTop + 10,
			Theme.withAlpha(Theme.ACCENT_BRIGHT, progress), true);

		if (this.breakpoint.isAtLeast(Breakpoint.REGULAR)) {
			String subtitle = "Selector inteligente de mods para Minecraft "
				+ dev.zymekoh.optimatch.catalog.ModrinthClient.gameVersion();
			Draw.clippedText(graphics, this.font, subtitle, textX, frameTop + 20,
				this.frameWidth - this.padding * 2, Theme.withAlpha(Theme.TEXT_DIM, progress * 0.85F), false);
		}

		int tabY = frameTop + this.tabRowY;
		int tabWidth = this.tabWidth();

		for (int index = 0; index < this.tabs.length; index++) {
			int tabX = this.tabX(index);
			boolean selected = index == this.activeTab;
			boolean hovered = Draw.inside(mouseX, mouseY, tabX, tabY, tabWidth, this.tabHeight);

			int fill = selected ? Theme.PANEL_HOVER : hovered ? Theme.PANEL_RAISED : Theme.PANEL;
			int border = selected ? Theme.ACCENT_BRIGHT : hovered ? Theme.ACCENT : Theme.BORDER_SOFT;
			Draw.roundedRect(graphics, tabX, tabY, tabWidth, this.tabHeight, 4, Theme.withAlpha(border, progress));
			Draw.roundedRect(graphics, tabX + 1, tabY + 1, tabWidth - 2, this.tabHeight - 2, 3,
				Theme.withAlpha(fill, progress));

			if (selected) {
				float pulse = Draw.wave(now, 1600L);
				int glow = Theme.argb(Math.round((160 + pulse * 90) * progress), Theme.ACCENT_RGB);
				graphics.fill(tabX + 4, tabY + this.tabHeight - 2, tabX + tabWidth - 4, tabY + this.tabHeight - 1, glow);
			}

			OptiTab tab = this.tabs[index];
			String label = (this.breakpoint.isCompact() ? tab.shortTitle() : tab.title()).getString();
			if (this.font.width(label) > tabWidth - 8) {
				label = this.font.plainSubstrByWidth(label, tabWidth - 8);
			}
			int labelColor = selected ? Theme.TEXT : hovered ? Theme.TEXT_MUTED : Theme.TEXT_DIM;
			graphics.centeredText(this.font, label, tabX + tabWidth / 2,
				tabY + (this.tabHeight - 8) / 2, Theme.withAlpha(labelColor, progress));
		}
	}

	private void drawPendingBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float progress, int frameTop) {
		if (this.pendingBarHeight <= 0) {
			return;
		}
		int barY = frameTop + this.frameHeight - this.footerHeight - this.pendingBarHeight;
		int barX = this.frameX + this.padding;
		int barWidth = this.frameWidth - this.padding * 2;

		Draw.roundedRect(graphics, barX, barY, barWidth, this.pendingBarHeight - 3, 4,
			Theme.argb(Math.round(55 * progress), Theme.WARN & 0xFFFFFF));
		Draw.outline(graphics, barX, barY, barWidth, this.pendingBarHeight - 3,
			Theme.argb(Math.round(110 * progress), Theme.WARN & 0xFFFFFF));

		String message = PendingChanges.count() + " mods listos para el proximo arranque";
		Draw.clippedText(graphics, this.font, message, barX + 8, barY + 6,
			barWidth - this.quitWidth - 24, Theme.withAlpha(Theme.WARN, progress), false);

		int buttonY = barY + 2;
		int buttonHeight = this.pendingBarHeight - 7;
		boolean hovered = Draw.inside(mouseX, mouseY, this.quitX, buttonY, this.quitWidth, buttonHeight);
		Draw.roundedRect(graphics, this.quitX, buttonY, this.quitWidth, buttonHeight, 3,
			Theme.withAlpha(hovered ? Theme.DANGER : Theme.BORDER, progress));
		Draw.roundedRect(graphics, this.quitX + 1, buttonY + 1, this.quitWidth - 2, buttonHeight - 2, 2,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, progress));
		graphics.centeredText(this.font, this.font.plainSubstrByWidth(
				this.confirmingQuit ? "Confirmar cierre" : "Cerrar Minecraft ahora", this.quitWidth - 6),
			this.quitX + this.quitWidth / 2, buttonY + (buttonHeight - 8) / 2,
			Theme.withAlpha(this.confirmingQuit ? Theme.DANGER : Theme.TEXT, progress));
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float progress, int frameTop) {
		int footerTop = frameTop + this.frameHeight - this.footerHeight;
		Draw.divider(graphics, this.frameX + 1, footerTop, this.frameWidth - 2, progress);

		int buttonY = footerTop + (this.footerHeight - this.continueHeight) / 2;

		if (this.breakpoint.isAtLeast(Breakpoint.REGULAR)) {
			String hint = "Esc para saltar al menu de Minecraft  ·  Tab para cambiar de pestana";
			int hintX = this.frameX + this.padding;
			if (hintX + this.font.width(hint) < this.continueX - 10) {
				graphics.text(this.font, hint, hintX, buttonY + (this.continueHeight - 8) / 2,
					Theme.withAlpha(Theme.TEXT_DIM, progress), false);
			}
		}

		boolean hovered = Draw.inside(mouseX, mouseY, this.continueX, buttonY, this.continueWidth, this.continueHeight);
		Draw.roundedRect(graphics, this.continueX, buttonY, this.continueWidth, this.continueHeight, 4,
			Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.ACCENT, progress));
		Draw.roundedRect(graphics, this.continueX + 1, buttonY + 1, this.continueWidth - 2, this.continueHeight - 2, 3,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, progress));

		String label = this.breakpoint.isCompact() ? "Jugar" : "Continuar a Minecraft";
		graphics.centeredText(this.font, this.font.plainSubstrByWidth(label, this.continueWidth - 8),
			this.continueX + this.continueWidth / 2, buttonY + (this.continueHeight - 8) / 2,
			Theme.withAlpha(Theme.TEXT, progress));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		double mouseX = this.ui.toVirtual(event.x());
		double mouseY = this.ui.toVirtual(event.y());

		if (this.dialog != null) {
			this.dialog.mouseClicked(mouseX, mouseY, event.button());
			this.settleDialog();
			return true;
		}

		double bodyMouseY = mouseY - this.liftOffset();

		int tabY = this.frameY + this.tabRowY;
		int tabWidth = this.tabWidth();
		for (int index = 0; index < this.tabs.length; index++) {
			if (Draw.inside(mouseX, bodyMouseY, this.tabX(index), tabY, tabWidth, this.tabHeight)) {
				this.selectTab(index);
				return true;
			}
		}

		if (this.pendingBarHeight > 0) {
			int barY = this.frameY + this.frameHeight - this.footerHeight - this.pendingBarHeight;
			if (Draw.inside(mouseX, bodyMouseY, this.quitX, barY + 2, this.quitWidth, this.pendingBarHeight - 7)) {
				if (this.confirmingQuit && this.minecraft != null) {
					this.minecraft.stop();
				} else {
					this.confirmingQuit = true;
				}
				return true;
			}
		}
		this.confirmingQuit = false;

		if (Draw.inside(mouseX, bodyMouseY, this.continueX, this.continueY, this.continueWidth, this.continueHeight)) {
			this.onClose();
			return true;
		}

		if (this.tabs[this.activeTab].mouseClicked(mouseX, bodyMouseY, event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double virtualX = this.ui.toVirtual(mouseX);
		double virtualY = this.ui.toVirtual(mouseY);

		if (this.dialog != null) {
			return this.dialog.mouseScrolled(virtualX, virtualY, verticalAmount);
		}
		if (this.tabs[this.activeTab].mouseScrolled(virtualX, virtualY - this.liftOffset(), verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (this.dialog != null) {
			if (!this.dialog.keyPressed(key, event.modifiers()) && key == GLFW.GLFW_KEY_ESCAPE) {
				this.dialog = null;
				this.init();
				return true;
			}
			this.settleDialog();
			return true;
		}

		// The active tab gets first refusal, so typing in the search box wins over shortcuts.
		if (this.tabs[this.activeTab].keyPressed(key)) {
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB) {
			this.selectTab(Math.floorMod(this.activeTab + 1, this.tabs.length));
			return true;
		}
		if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + this.tabs.length) {
			this.selectTab(key - GLFW.GLFW_KEY_1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.dialog != null) {
			this.dialog.charTyped(event.codepoint());
			return true;
		}
		if (this.tabs[this.activeTab].charTyped(event.codepoint())) {
			return true;
		}
		return super.charTyped(event);
	}

	/** Tabs open dialogs through here so the panel is always sized before its first frame. */
	private void openDialog(Dialog opened) {
		this.dialog = opened;
		this.dialog.layout(this.ui.width(), this.ui.height());
	}

	/**
	 * Closes a finished dialog, opening whatever it wanted to hand over to. That is what lets the
	 * config browser and the editor push and pop each other without the screen tracking a stack.
	 */
	private void settleDialog() {
		if (this.dialog == null || !this.dialog.isClosed()) {
			return;
		}
		Dialog next = this.dialog.successor();
		this.dialog = null;
		// A finished install may have added pending entries, so the bar has to be recomputed.
		this.init();
		if (next != null) {
			this.openDialog(next);
		}
	}

	private void selectTab(int index) {
		if (index != this.activeTab) {
			this.activeTab = index;
			this.tabChangedAt = Util.getMillis();
			this.tabs[index].onSelected();
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft == null) {
			return;
		}
		// Fall back to a fresh title screen when opened straight from the launch hook.
		this.minecraft.setScreen(this.parent != null ? this.parent : new TitleScreen());
	}

	/**
	 * At the title screen this must not pause anything, so the panorama keeps moving behind the
	 * panel. But Mod Menu can also open the selector from the in-game pause menu, and there the
	 * world has to stay paused while the player reads.
	 */
	@Override
	public boolean isPauseScreen() {
		return this.minecraft != null && this.minecraft.level != null;
	}
}

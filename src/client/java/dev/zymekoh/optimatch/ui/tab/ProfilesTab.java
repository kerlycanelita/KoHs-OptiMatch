package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.profile.ModProfile;
import dev.zymekoh.optimatch.profile.ProfileStore;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 4 — snapshots of the mod folder the player can name and come back to, e.g. "PvP" or
 * "Grabar video". Saving records which mods were active; the list shows how each snapshot differs
 * from what is loaded right now.
 */
public final class ProfilesTab implements OptiTab {
	private static final DateTimeFormatter STAMP =
		DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
	private static final int ROW_HEIGHT = 30;

	private List<ModProfile> profiles = List.of();
	private List<String> currentModIds = List.of();

	private int x;
	private int y;
	private int width;
	private int height;
	private int saveButtonX;
	private int saveButtonY;
	private int saveButtonWidth;
	private int saveButtonHeight;

	private int scroll;
	private int maxScroll;
	private int selected = -1;

	private Breakpoint breakpoint = Breakpoint.REGULAR;

	@Override
	public Component title() {
		return Component.literal("Perfiles");
	}

	@Override
	public void onSelected() {
		this.profiles = ProfileStore.profiles();
		if (this.currentModIds.isEmpty()) {
			List<String> ids = new ArrayList<>();
			for (InstalledMod mod : ModScanner.scan()) {
				if (mod.isUserFacing()) {
					ids.add(mod.id());
				}
			}
			this.currentModIds = List.copyOf(ids);
		}
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;
		this.saveButtonWidth = Math.min(190, Math.max(70, width / breakpoint.pick(2, 3, 3)));
		this.saveButtonHeight = 18;
		this.saveButtonX = x + width - this.saveButtonWidth - 8;
		this.saveButtonY = y + 5;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		Draw.panel(graphics, this.x, this.y, this.width, this.height, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		String heading = this.breakpoint.isCompact()
			? this.profiles.size() + " perfiles"
			: this.profiles.size() + " perfiles guardados";
		Draw.clippedText(graphics, font, heading, this.x + 8, this.y + 9,
			Math.max(20, this.saveButtonX - this.x - 14), Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);

		boolean hovered = Draw.inside(mouseX, mouseY, this.saveButtonX, this.saveButtonY,
			this.saveButtonWidth, this.saveButtonHeight);
		Draw.roundedRect(graphics, this.saveButtonX, this.saveButtonY, this.saveButtonWidth, this.saveButtonHeight, 4,
			Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
		Draw.roundedRect(graphics, this.saveButtonX + 1, this.saveButtonY + 1,
			this.saveButtonWidth - 2, this.saveButtonHeight - 2, 3,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
		graphics.centeredText(font, Component.literal(
				this.breakpoint.isCompact() ? "Guardar" : "Guardar setup actual"),
			this.saveButtonX + this.saveButtonWidth / 2, this.saveButtonY + 5,
			Theme.withAlpha(Theme.TEXT, opacity));

		int listTop = this.y + 26;
		int listBottom = this.y + this.height - 2;

		if (this.profiles.isEmpty()) {
			graphics.centeredText(font, Component.literal("Aun no has guardado ningun perfil"),
				this.x + this.width / 2, listTop + 20, Theme.withAlpha(Theme.TEXT_DIM, opacity));
			graphics.centeredText(font,
				Component.literal("Guarda tu combinacion actual de " + this.currentModIds.size() + " mods para volver a ella"),
				this.x + this.width / 2, listTop + 32, Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.8F));
			return;
		}

		graphics.enableScissor(this.x + 1, listTop, this.x + this.width - 1, listBottom);
		int cursorY = listTop - this.scroll;

		for (int index = 0; index < this.profiles.size(); index++) {
			ModProfile profile = this.profiles.get(index);
			if (cursorY + ROW_HEIGHT >= listTop && cursorY <= listBottom) {
				boolean isSelected = index == this.selected;
				boolean rowHovered = Draw.inside(mouseX, mouseY, this.x + 6, cursorY, this.width - 12, ROW_HEIGHT - 3);

				Draw.roundedRect(graphics, this.x + 6, cursorY, this.width - 12, ROW_HEIGHT - 3, 4,
					Theme.withAlpha(isSelected ? Theme.PANEL_HOVER : rowHovered ? Theme.PANEL_RAISED : Theme.PANEL, opacity));

				Draw.clippedText(graphics, font, profile.name(), this.x + 12, cursorY + 5,
					this.width - 110, Theme.withAlpha(Theme.TEXT, opacity), false);

				String meta = profile.size() + " mods  ·  " + STAMP.format(Instant.ofEpochMilli(profile.savedAt()));
				Draw.clippedText(graphics, font, meta, this.x + 12, cursorY + 16,
					this.width - 110, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

				// How this snapshot differs from what is loaded right now.
				int missing = 0;
				for (String modId : profile.modIds()) {
					if (!this.currentModIds.contains(modId)) {
						missing++;
					}
				}
				String diff = missing == 0 ? "coincide con lo cargado" : "faltan " + missing;
				int diffColor = missing == 0 ? Theme.GOOD : Theme.WARN;
				graphics.text(font, diff, this.x + this.width - font.width(diff) - 14, cursorY + 10,
					Theme.withAlpha(diffColor, opacity), false);
			}
			cursorY += ROW_HEIGHT;
		}

		graphics.disableScissor();

		int contentHeight = this.profiles.size() * ROW_HEIGHT;
		this.maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Draw.inside(mouseX, mouseY, this.saveButtonX, this.saveButtonY, this.saveButtonWidth, this.saveButtonHeight)) {
			ProfileStore.save(new ModProfile(defaultName(), this.currentModIds, System.currentTimeMillis()));
			this.profiles = ProfileStore.profiles();
			return true;
		}

		int listTop = this.y + 26;
		if (Draw.inside(mouseX, mouseY, this.x, listTop, this.width, this.height - 28)) {
			int index = (int) ((mouseY - listTop + this.scroll) / ROW_HEIGHT);
			if (index >= 0 && index < this.profiles.size()) {
				this.selected = index;
				return true;
			}
		}
		return false;
	}

	/** Profiles are named by their save time until renaming lands. */
	private String defaultName() {
		return "Setup " + STAMP.format(Instant.now());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0 || !Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.height)) {
			return false;
		}
		this.scroll = Mth.clamp(this.scroll - (int) Math.round(amount * ROW_HEIGHT), 0, this.maxScroll);
		return true;
	}
}

package dev.zymekoh.optimatch.ui.tab;

import dev.zymekoh.optimatch.OptiMatchClient;
import dev.zymekoh.optimatch.install.ModInstaller;
import dev.zymekoh.optimatch.profile.ModProfile;
import dev.zymekoh.optimatch.profile.ProfileStore;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Breakpoint;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.OptiTab;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.dialog.Dialog;
import dev.zymekoh.optimatch.ui.dialog.ProfileConfirmDialog;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tab 5 — saved setups. A profile stores which mods were active, the slug needed to get each one
 * back, and the contents of their config files, so returning to it restores settings too.
 *
 * <p>Selecting a profile lists its mods against what is loaded right now: the ones you are missing
 * are greyed out, and that missing set is exactly what the install button offers to download.
 */
public final class ProfilesTab implements OptiTab {
	private static final DateTimeFormatter STAMP =
		DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
	private static final int PROFILE_ROW = 28;
	private static final int MOD_ROW = 20;

	private final Consumer<Dialog> dialogOpener;

	private List<ModProfile> profiles = List.of();
	private List<InstalledMod> installed = List.of();
	private Set<String> installedIds = Set.of();
	private int selected = -1;
	private String status;

	private Breakpoint breakpoint = Breakpoint.REGULAR;
	private boolean stacked;

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

	private int saveX;
	private int saveY;
	private int saveWidth;
	private int saveHeight;
	private int actionY;
	private int actionHeight;
	private int installX;
	private int installWidth;
	private int deleteX;
	private int deleteWidth;

	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	public ProfilesTab(Consumer<Dialog> dialogOpener) {
		this.dialogOpener = dialogOpener;
	}

	@Override
	public Component title() {
		return Component.literal("Perfiles");
	}

	@Override
	public Component shortTitle() {
		return Component.literal("Perfiles");
	}

	@Override
	public void onSelected() {
		this.refresh();
	}

	private void refresh() {
		this.profiles = ProfileStore.profiles();
		this.installed = ModScanner.scan();

		Set<String> ids = new HashSet<>();
		for (InstalledMod mod : this.installed) {
			if (mod.isUserFacing()) {
				ids.add(mod.id());
			}
		}
		this.installedIds = Set.copyOf(ids);
		ModIcons.preload(this.installed);

		if (this.selected >= this.profiles.size()) {
			this.selected = this.profiles.isEmpty() ? -1 : 0;
		}
	}

	@Override
	public void layout(int x, int y, int width, int height, Breakpoint breakpoint) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.breakpoint = breakpoint;
		this.stacked = breakpoint.isCompact();

		this.saveHeight = 18;
		this.saveWidth = Math.min(180, Math.max(70, width / breakpoint.pick(2, 3, 4)));
		this.saveX = x + width - this.saveWidth;
		this.saveY = y;

		int contentTop = y + this.saveHeight + 6;
		int contentHeight = Math.max(40, height - this.saveHeight - 6);

		if (this.stacked) {
			int listShare = Math.max(50, Math.round(contentHeight * 0.42F));
			this.listX = x;
			this.listY = contentTop;
			this.listWidth = width;
			this.listHeight = listShare;
			this.detailX = x;
			this.detailY = contentTop + listShare + 4;
			this.detailWidth = width;
			this.detailHeight = Math.max(30, contentHeight - listShare - 4);
		} else {
			int split = Math.max(130, Math.round(width * 0.38F));
			this.listX = x;
			this.listY = contentTop;
			this.listWidth = split;
			this.listHeight = contentHeight;
			this.detailX = x + split + 6;
			this.detailY = contentTop;
			this.detailWidth = Math.max(90, width - split - 6);
			this.detailHeight = contentHeight;
		}

		// The action bar lives along the bottom of the detail pane.
		this.actionHeight = 18;
		this.actionY = this.detailY + this.detailHeight - this.actionHeight - 2;
		this.deleteWidth = Math.min(120, Math.max(64, this.detailWidth / 3));
		this.installWidth = Math.min(190, Math.max(80, this.detailWidth / 2));
		this.deleteX = this.detailX + this.detailWidth - this.deleteWidth - 4;
		this.installX = this.deleteX - this.installWidth - 5;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		this.renderSaveButton(graphics, font, mouseX, mouseY, opacity);
		this.renderProfileList(graphics, font, mouseX, mouseY, opacity);
		this.renderDetail(graphics, font, mouseX, mouseY, opacity);
	}

	private void renderSaveButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		String heading = this.profiles.size() + (this.profiles.size() == 1 ? " perfil" : " perfiles");
		Draw.clippedText(graphics, font, heading, this.x, this.y + 5,
			Math.max(20, this.saveX - this.x - 8), Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), false);

		if (this.status != null) {
			int statusX = this.x + font.width(heading) + 10;
            Draw.clippedText(graphics, font, this.status, statusX, this.y + 5,
				Math.max(10, this.saveX - statusX - 8), Theme.withAlpha(Theme.GOOD, opacity), false);
		}

		boolean hovered = Draw.inside(mouseX, mouseY, this.saveX, this.saveY, this.saveWidth, this.saveHeight);
		Draw.roundedRect(graphics, this.saveX, this.saveY, this.saveWidth, this.saveHeight, 4,
			Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
		Draw.roundedRect(graphics, this.saveX + 1, this.saveY + 1, this.saveWidth - 2, this.saveHeight - 2, 3,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
		graphics.centeredText(font, this.breakpoint.isCompact() ? "Guardar" : "Guardar setup actual",
			this.saveX + this.saveWidth / 2, this.saveY + 5, Theme.withAlpha(Theme.TEXT, opacity));

		if (hovered) {
			Tooltip.request("Guardar el setup actual",
				"Guarda tus " + this.installedIds.size() + " mods y el contenido de sus archivos de "
					+ "configuracion, para poder volver exactamente a este estado.", mouseX, mouseY);
		}
	}

	private void renderProfileList(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		Draw.panel(graphics, this.listX, this.listY, this.listWidth, this.listHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		if (this.profiles.isEmpty()) {
			graphics.centeredText(font, "Sin perfiles guardados",
				this.listX + this.listWidth / 2, this.listY + this.listHeight / 2 - 4,
				Theme.withAlpha(Theme.TEXT_DIM, opacity));
			return;
		}

		graphics.enableScissor(this.listX + 1, this.listY + 1, this.listX + this.listWidth - 1,
			this.listY + this.listHeight - 1);

		for (int index = 0; index < this.profiles.size(); index++) {
			ModProfile profile = this.profiles.get(index);
			int rowY = this.listY + 4 + index * PROFILE_ROW;
			if (rowY > this.listY + this.listHeight) {
				break;
			}

			boolean isSelected = index == this.selected;
			boolean hovered = Draw.inside(mouseX, mouseY, this.listX + 4, rowY, this.listWidth - 8, PROFILE_ROW - 3);
			if (isSelected || hovered) {
				Draw.roundedRect(graphics, this.listX + 4, rowY, this.listWidth - 8, PROFILE_ROW - 3, 3,
					Theme.withAlpha(isSelected ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
			}
			if (isSelected) {
				graphics.fill(this.listX + 4, rowY, this.listX + 6, rowY + PROFILE_ROW - 3,
					Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity));
			}

			Draw.clippedText(graphics, font, profile.name(), this.listX + 10, rowY + 4, this.listWidth - 20,
				Theme.withAlpha(isSelected ? Theme.TEXT : Theme.TEXT_MUTED, opacity), false);
			Draw.clippedText(graphics, font,
				profile.size() + " mods · " + profile.configCount() + " configs",
				this.listX + 10, rowY + 14, this.listWidth - 20,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		}

		graphics.disableScissor();
	}

	/** Right pane: the selected profile's mods, with the ones you are missing greyed out. */
	private void renderDetail(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		Draw.panel(graphics, this.detailX, this.detailY, this.detailWidth, this.detailHeight, 6,
			Theme.withAlpha(Theme.PANEL, opacity), Theme.withAlpha(Theme.BORDER_SOFT, opacity));

		ModProfile profile = this.selectedProfile();
		if (profile == null) {
			graphics.centeredText(font, "Elige un perfil para ver sus mods",
				this.detailX + this.detailWidth / 2, this.detailY + this.detailHeight / 2 - 4,
				Theme.withAlpha(Theme.TEXT_DIM, opacity));
			return;
		}

		int textX = this.detailX + 8;
		int maxWidth = this.detailWidth - 16;

		Draw.clippedText(graphics, font, profile.name(), textX, this.detailY + 6, maxWidth - 90,
			Theme.withAlpha(Theme.TEXT, opacity), false);
		String stamp = STAMP.format(Instant.ofEpochMilli(profile.savedAt()));
		graphics.text(font, stamp, this.detailX + this.detailWidth - font.width(stamp) - 8, this.detailY + 6,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

		List<ModProfile.Entry> missing = this.missingOf(profile);
		String summary = missing.isEmpty()
			? "Coincide con lo que tienes cargado"
			: "Te faltan " + missing.size() + " de " + profile.size();
		Draw.clippedText(graphics, font, summary, textX, this.detailY + 17, maxWidth,
			Theme.withAlpha(missing.isEmpty() ? Theme.GOOD : Theme.WARN, opacity), false);

		int listTop = this.detailY + 30;
		int listBottom = this.actionY - 4;
		graphics.enableScissor(this.detailX + 1, listTop, this.detailX + this.detailWidth - 1, listBottom);

		int cursorY = listTop - this.scroll.intValue();
		for (ModProfile.Entry entry : profile.entries()) {
			// "Disabled" here means: part of this profile, but not currently loaded.
			boolean present = this.installedIds.contains(entry.modId());

			if (cursorY + MOD_ROW >= listTop && cursorY <= listBottom) {
				int icon = 14;
				ModIcons.draw(graphics, font, entry.modId(), entry.displayName(),
					textX + 2, cursorY + 2, icon, present ? Theme.ACCENT : Theme.TEXT_DIM,
					opacity * (present ? 1.0F : 0.45F));

				int nameX = textX + icon + 8;
				Draw.clippedText(graphics, font, entry.displayName(), nameX, cursorY + 2,
					maxWidth - (nameX - textX) - 60,
					Theme.withAlpha(present ? Theme.TEXT_MUTED : Theme.TEXT_DIM, opacity * (present ? 1.0F : 0.6F)),
					false);
				Draw.clippedText(graphics, font, "v" + entry.version(), nameX, cursorY + 11,
					maxWidth - (nameX - textX) - 60,
					Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.75F), false);

				String tag = present ? "instalado" : entry.isReinstallable() ? "falta" : "sin origen";
				int tagColor = present ? Theme.GOOD : entry.isReinstallable() ? Theme.WARN : Theme.DANGER;
				graphics.text(font, tag, this.detailX + this.detailWidth - font.width(tag) - 10, cursorY + 6,
					Theme.withAlpha(tagColor, opacity), false);

				if (Draw.inside(mouseX, mouseY, textX, cursorY, maxWidth, MOD_ROW)) {
					Tooltip.request(entry.displayName(), present
						? "Ya lo tienes cargado."
						: entry.isReinstallable()
							? "Falta en tu carpeta. Se descargaria de Modrinth al instalar el perfil."
							: "Falta, y no se guardo su origen en Modrinth, asi que hay que ponerlo a mano.",
						mouseX, mouseY);
				}
			}
			cursorY += MOD_ROW;
		}

		graphics.disableScissor();

		int content = profile.entries().size() * MOD_ROW;
		this.maxScroll = Math.max(0, content - (listBottom - listTop));
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));

		this.renderActions(graphics, font, mouseX, mouseY, missing, opacity);
	}

	private void renderActions(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
							   List<ModProfile.Entry> missing, float opacity) {
		int installable = (int) missing.stream().filter(ModProfile.Entry::isReinstallable).count();
		boolean canInstall = installable > 0;

		boolean installHovered = canInstall
			&& Draw.inside(mouseX, mouseY, this.installX, this.actionY, this.installWidth, this.actionHeight);
		Draw.roundedRect(graphics, this.installX, this.actionY, this.installWidth, this.actionHeight, 4,
			Theme.withAlpha(!canInstall ? Theme.BORDER_SOFT : installHovered ? Theme.ACCENT_BRIGHT : Theme.ACCENT, opacity));
		Draw.roundedRect(graphics, this.installX + 1, this.actionY + 1, this.installWidth - 2, this.actionHeight - 2, 3,
			Theme.withAlpha(!canInstall ? Theme.PANEL : installHovered ? Theme.PANEL_HOVER : Theme.PANEL_RAISED, opacity));
		// A download glyph plus the count, so the action reads at a glance.
		String installLabel = canInstall ? "↓  Instalar " + installable + " que faltan" : "↓  Nada que instalar";
		graphics.centeredText(font, font.plainSubstrByWidth(installLabel, this.installWidth - 6),
			this.installX + this.installWidth / 2, this.actionY + 5,
			Theme.withAlpha(canInstall ? Theme.TEXT : Theme.TEXT_DIM, opacity));
		if (installHovered) {
			Tooltip.request("Instalar lo que falta",
				"Muestra la lista exacta antes de descargar nada. Los mods quedan listos para el proximo arranque.",
				mouseX, mouseY);
		}

		boolean deleteHovered = Draw.inside(mouseX, mouseY, this.deleteX, this.actionY, this.deleteWidth, this.actionHeight);
		Draw.roundedRect(graphics, this.deleteX, this.actionY, this.deleteWidth, this.actionHeight, 4,
			Theme.withAlpha(deleteHovered ? Theme.DANGER : Theme.BORDER_SOFT, opacity));
		Draw.roundedRect(graphics, this.deleteX + 1, this.actionY + 1, this.deleteWidth - 2, this.actionHeight - 2, 3,
			Theme.withAlpha(deleteHovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
		graphics.centeredText(font, "Eliminar", this.deleteX + this.deleteWidth / 2, this.actionY + 5,
			Theme.withAlpha(deleteHovered ? Theme.DANGER : Theme.TEXT_DIM, opacity));
		if (deleteHovered) {
			Tooltip.request("Eliminar el perfil",
				"Borra el perfil y tambien los jars y configs de sus mods. Pide confirmacion primero.",
				mouseX, mouseY);
		}
	}

	private ModProfile selectedProfile() {
		return this.selected >= 0 && this.selected < this.profiles.size()
			? this.profiles.get(this.selected)
			: null;
	}

	/** Entries in the profile that are not currently loaded. */
	private List<ModProfile.Entry> missingOf(ModProfile profile) {
		List<ModProfile.Entry> missing = new ArrayList<>();
		for (ModProfile.Entry entry : profile.entries()) {
			if (!this.installedIds.contains(entry.modId())) {
				missing.add(entry);
			}
		}
		return missing;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (Draw.inside(mouseX, mouseY, this.saveX, this.saveY, this.saveWidth, this.saveHeight)) {
			ModProfile saved = ProfileStore.capture("Setup " + STAMP.format(Instant.now()), this.installed);
			ProfileStore.save(saved);
			this.status = "Guardado con " + saved.configCount() + " configs";
			this.refresh();
			this.selected = 0;
			return true;
		}

		if (Draw.inside(mouseX, mouseY, this.listX, this.listY, this.listWidth, this.listHeight)) {
			int index = (int) ((mouseY - this.listY - 4) / PROFILE_ROW);
			if (index >= 0 && index < this.profiles.size()) {
				this.selected = index;
				this.scroll.snapTo(0);
				return true;
			}
		}

		ModProfile profile = this.selectedProfile();
		if (profile == null) {
			return false;
		}

		if (Draw.inside(mouseX, mouseY, this.installX, this.actionY, this.installWidth, this.actionHeight)) {
			List<ModProfile.Entry> missing = this.missingOf(profile).stream()
				.filter(ModProfile.Entry::isReinstallable)
				.toList();
			if (!missing.isEmpty()) {
				this.dialogOpener.accept(new ProfileConfirmDialog(
					ProfileConfirmDialog.Mode.INSTALL_MISSING, profile, missing,
					this::installAll, this::refresh));
			}
			return true;
		}

		if (Draw.inside(mouseX, mouseY, this.deleteX, this.actionY, this.deleteWidth, this.actionHeight)) {
			this.dialogOpener.accept(new ProfileConfirmDialog(
				ProfileConfirmDialog.Mode.DELETE_PROFILE, profile, profile.entries(),
				this::installAll, () -> {
					this.status = "Perfil eliminado";
					this.selected = -1;
					this.refresh();
				}));
			return true;
		}
		return false;
	}

	/** Downloads every missing mod of the profile, then restores its saved config files. */
	private void installAll(List<ModProfile.Entry> missing) {
		ModProfile profile = this.selectedProfile();

		for (ModProfile.Entry entry : missing) {
			ModInstaller.plan(entry.slug(), entry.displayName())
				.thenCompose(plan -> ModInstaller.execute(plan, progress -> { }))
				.exceptionally(throwable -> {
					OptiMatchClient.LOGGER.warn("Could not install {} from profile", entry.displayName(), throwable);
					return null;
				});
		}

		if (profile != null && profile.configCount() > 0) {
			int restored = ProfileStore.restoreConfigs(profile);
			this.status = "Descargando " + missing.size() + " mods · " + restored + " configs restauradas";
		} else {
			this.status = "Descargando " + missing.size() + " mods";
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll <= 0
			|| !Draw.inside(mouseX, mouseY, this.detailX, this.detailY, this.detailWidth, this.detailHeight)) {
			return false;
		}
		this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * MOD_ROW, 0, this.maxScroll));
		return true;
	}
}

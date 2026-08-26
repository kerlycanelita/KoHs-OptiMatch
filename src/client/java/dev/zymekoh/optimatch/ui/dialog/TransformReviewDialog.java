package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.transform.TransformApplier;
import dev.zymekoh.optimatch.transform.TransformPlan;
import dev.zymekoh.optimatch.transform.TransformPreset;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The pause before a change that is hard to see and easy to regret.
 *
 * <p>Shown in three situations, all of which are the same conversation at different points: a plan
 * that reaches into shared ground, a preview when the window is too narrow to show one inline, and
 * the report of what actually got written. It is deliberately not shown for ordinary changes — a
 * confirmation that appears every time teaches people to dismiss it without reading, which is exactly
 * the habit you do not want when the one that matters finally appears.
 */
public final class TransformReviewDialog implements Dialog {
	private static final long OPEN_MILLIS = 220L;

	private record Line(String text, int color, boolean strong) {
	}

	private final String title;
	private final String subtitle;
	private final List<Line> lines;
	private final String confirmLabel;
	private final String cancelLabel;
	private final Runnable onConfirm;
	private final int accent;

	private final long openedAt = Util.getMillis();
	private boolean closed;

	private int x;
	private int y;
	private int width;
	private int height;
	private int buttonY;
	private int buttonHeight;
	private int buttonWidth;

	private TransformReviewDialog(String title, String subtitle, List<Line> lines, String confirmLabel,
								  String cancelLabel, Runnable onConfirm, int accent) {
		this.title = title;
		this.subtitle = subtitle;
		this.lines = lines;
		this.confirmLabel = confirmLabel;
		this.cancelLabel = cancelLabel;
		this.onConfirm = onConfirm;
		this.accent = accent;
	}

	/** Something in this plan deserves a second look before it is written. */
	public static TransformReviewDialog review(TransformPlan plan, Runnable onConfirm) {
		List<Line> lines = new ArrayList<>();
		for (TransformPlan.Concern concern : plan.concerns()) {
			int color = switch (concern.severity()) {
				case BLOCKING -> Theme.DANGER;
				case SERIOUS -> Theme.WARN;
				case NOTE -> Theme.INFO;
			};
			lines.add(new Line(concern.title(), color, true));
			lines.add(new Line(concern.detail(), Theme.TEXT_MUTED, false));
		}
		if (!plan.applicable().isEmpty()) {
			lines.add(new Line("Se aplicara de todas formas:", Theme.TEXT, true));
			for (TransformPlan.Change change : plan.applicable()) {
				lines.add(new Line("· " + change.summary(), Theme.TEXT_MUTED, false));
			}
		}
		boolean blocked = plan.count(TransformPlan.Severity.BLOCKING) > 0;
		return new TransformReviewDialog(
			blocked ? "Esto no se puede hacer entero" : "Revisa antes de aplicar",
			plan.preset().name(), lines,
			plan.applicable().isEmpty() ? null : "Aplicar igualmente", "Cancelar", onConfirm,
			blocked ? Theme.DANGER : Theme.WARN);
	}

	/** The narrow-window preview: the same content the wide layout shows in its left column. */
	public static TransformReviewDialog preview(TransformPlan plan, Runnable onConfirm) {
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(plan.preset().detail(), Theme.TEXT_MUTED, false));
		if (plan.applicable().isEmpty()) {
			lines.add(new Line("Tu configuracion ya coincide con este preset.", Theme.TEXT_DIM, false));
		} else {
			lines.add(new Line(plan.applicable().size() + " cambios:", Theme.TEXT, true));
			for (TransformPlan.Change change : plan.applicable()) {
				lines.add(new Line("· " + change.summary(), Theme.TEXT_MUTED, false));
			}
		}
		for (TransformPlan.Concern concern : plan.concerns()) {
			lines.add(new Line(concern.title() + " — " + concern.detail(),
				concern.severity() == TransformPlan.Severity.NOTE ? Theme.TEXT_DIM : Theme.WARN, false));
		}
		return new TransformReviewDialog(plan.preset().name(), plan.preset().tagline(), lines,
			plan.applicable().isEmpty() ? null : "Aplicar", "Cerrar", onConfirm, Theme.ACCENT_BRIGHT);
	}

	/** What actually got written, once it is done. */
	public static TransformReviewDialog outcome(TransformPreset preset, TransformApplier.Result result) {
		List<Line> lines = new ArrayList<>();
		for (String applied : result.applied()) {
			lines.add(new Line("· " + applied, Theme.GOOD, false));
		}
		for (String failed : result.failed()) {
			lines.add(new Line("· " + failed, Theme.DANGER, false));
		}
		if (result.ok() && result.total() > 0) {
			lines.add(new Line("Los mods leen estos archivos al arrancar, asi que el cambio entra "
				+ "en el proximo inicio de Minecraft.", Theme.TEXT_MUTED, false));
		}
		return new TransformReviewDialog(
			result.ok() ? "Escrito" : "Escrito a medias",
			preset == null ? "" : preset.name(), lines, null, "Entendido", null,
			result.ok() ? Theme.GOOD : Theme.WARN);
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(420, Math.max(260, canvasWidth - 80));
		this.height = Math.min(250, Math.max(150, canvasHeight - 90));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;
		this.buttonHeight = 24;
		this.buttonY = this.y + this.height - this.buttonHeight - 10;
		this.buttonWidth = this.confirmLabel == null
			? this.width - 24
			: (this.width - 28) / 2;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(150 * opacity), 0x05020B));

		float appear = Anim.easeOutBack(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.94F + 0.06F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		Draw.window(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), opacity);

		int textX = this.x + 12;
		int textWidth = this.width - 24;
		graphics.text(font, this.title, textX, this.y + 12, Theme.withAlpha(this.accent, opacity), true);
		Draw.clippedText(graphics, font, this.subtitle, textX, this.y + 24, textWidth,
			Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		Draw.divider(graphics, textX, this.y + 36, textWidth, opacity);

		// The body stops where the buttons begin, so nothing can ever be drawn underneath them.
		int cursorY = this.y + 42;
		int bodyBottom = this.buttonY - 8;
		graphics.enableScissor(this.x + 1, this.y + 40, this.x + this.width - 1, bodyBottom);
		for (Line line : this.lines) {
			if (cursorY >= bodyBottom) {
				break;
			}
			if (line.strong()) {
				Draw.clippedText(graphics, font, line.text(), textX, cursorY, textWidth,
					Theme.withAlpha(line.color(), opacity), false);
				cursorY += 11;
			} else {
				cursorY = Draw.wrappedText(graphics, font, line.text(), textX, cursorY, textWidth, 3,
					line.color(), opacity) + 2;
			}
		}
		graphics.disableScissor();

		this.renderButtons(graphics, font, mouseX, mouseY, opacity);
		graphics.pose().popMatrix();
	}

	private void renderButtons(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		if (this.confirmLabel != null) {
			this.button(graphics, font, this.x + 12, this.confirmLabel, this.accent, mouseX, mouseY, opacity);
			this.button(graphics, font, this.x + 16 + this.buttonWidth, this.cancelLabel, Theme.BORDER,
				mouseX, mouseY, opacity);
		} else {
			this.button(graphics, font, this.x + 12, this.cancelLabel, this.accent, mouseX, mouseY, opacity);
		}
	}

	private void button(GuiGraphicsExtractor graphics, Font font, int buttonX, String label, int border,
						int mouseX, int mouseY, float opacity) {
		boolean hovered = Draw.inside(mouseX, mouseY, buttonX, this.buttonY, this.buttonWidth, this.buttonHeight);
		Draw.roundedRect(graphics, buttonX, this.buttonY, this.buttonWidth, this.buttonHeight, 4,
			Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : border, opacity));
		Draw.roundedRect(graphics, buttonX + 1, this.buttonY + 1, this.buttonWidth - 2,
			this.buttonHeight - 2, 3, Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
		graphics.centeredText(font, font.plainSubstrByWidth(label, this.buttonWidth - 8),
			buttonX + this.buttonWidth / 2, this.buttonY + 8, Theme.withAlpha(Theme.TEXT, opacity));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.confirmLabel != null
			&& Draw.inside(mouseX, mouseY, this.x + 12, this.buttonY, this.buttonWidth, this.buttonHeight)) {
			this.closed = true;
			Tooltip.clear();
			if (this.onConfirm != null) {
				this.onConfirm.run();
			}
			return true;
		}
		int cancelX = this.confirmLabel == null ? this.x + 12 : this.x + 16 + this.buttonWidth;
		if (Draw.inside(mouseX, mouseY, cancelX, this.buttonY, this.buttonWidth, this.buttonHeight)) {
			this.closed = true;
			Tooltip.clear();
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			this.closed = true;
			Tooltip.clear();
			return true;
		}
		return false;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}

package dev.zymekoh.optimatch.ui.dialog;

import dev.zymekoh.optimatch.config.ModConfigLocator;
import dev.zymekoh.optimatch.scan.ClassGlossary;
import dev.zymekoh.optimatch.scan.Conflict;
import dev.zymekoh.optimatch.scan.InstalledMod;
import dev.zymekoh.optimatch.scan.MixinTarget;
import dev.zymekoh.optimatch.scan.ModScanner;
import dev.zymekoh.optimatch.scan.TargetInspector;
import dev.zymekoh.optimatch.ui.Anim;
import dev.zymekoh.optimatch.ui.Draw;
import dev.zymekoh.optimatch.ui.ModIcons;
import dev.zymekoh.optimatch.ui.Theme;
import dev.zymekoh.optimatch.ui.Tooltip;
import dev.zymekoh.optimatch.ui.VersusBanner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Everything known about one conflict: what the target actually is, what each mod does to it, who
 * Mixin will pick, and what can honestly be done about it.
 *
 * <p>The facts come from bytecode — the target's real signature is read out of the Minecraft class
 * on the classpath, and the injection kinds and priorities out of each mod's mixin classes. Mojang
 * publishes mappings, not behaviour docs, so the prose context is this tool's own.
 */
public final class ConflictDetailDialog implements Dialog {
	private static final long OPEN_MILLIS = 240L;

	private final Conflict conflict;
	private final TargetInspector.MethodInfo target;
	private final List<String> overloads;
	private final long openedAt = Util.getMillis();

	private Dialog successor;
	private boolean closed;

	private final Anim.Smoothed scroll = new Anim.Smoothed(16.0F);
	private int maxScroll;

	/** Per-contender "open this mod's config" buttons, rebuilt each frame. */
	private final List<ActionSpot> actions = new ArrayList<>();

	private record ActionSpot(int x, int y, int width, int height, String modId) {
	}

	private int x;
	private int y;
	private int width;
	private int height;
	private int bodyTop;
	private int bodyBottom;
	private int closeX;
	private int closeY;
	private int closeWidth;
	private int closeHeight;

	public ConflictDetailDialog(Conflict conflict) {
		this.conflict = conflict;
		this.target = TargetInspector.inspect(conflict.targetClass(), conflict.plainMethodName());
		this.overloads = TargetInspector.overloadsOf(conflict.targetClass(), conflict.plainMethodName());
	}

	@Override
	public void layout(int canvasWidth, int canvasHeight) {
		this.width = Math.min(580, Math.max(260, canvasWidth - 60));
		this.height = Math.min(420, Math.max(180, canvasHeight - 50));
		this.x = (canvasWidth - this.width) / 2;
		this.y = (canvasHeight - this.height) / 2;

		this.closeWidth = 90;
		this.closeHeight = 20;
		this.closeX = this.x + this.width - this.closeWidth - 12;
		this.closeY = this.y + this.height - this.closeHeight - 10;

		this.bodyTop = this.y + (this.conflict.isDuel() ? 74 : 46);
		this.bodyBottom = this.closeY - 8;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, long now, float opacity) {
		graphics.fill(0, 0, 10000, 10000, Theme.argb(Math.round(160 * opacity), 0x05020B));

		float appear = Anim.easeOutBack(Anim.progress(this.openedAt, OPEN_MILLIS));
		float scale = 0.94F + 0.06F * appear;
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.x + this.width / 2.0F, this.y + this.height / 2.0F);
		graphics.pose().scale(scale);
		graphics.pose().translate(-(this.x + this.width / 2.0F), -(this.y + this.height / 2.0F));

		int accent = levelColor(this.conflict.level());
		Draw.panel(graphics, this.x, this.y, this.width, this.height, 8,
			Theme.withAlpha(Theme.PANEL_RAISED, opacity), Theme.withAlpha(accent, opacity));

		this.renderHeader(graphics, font, now, opacity);
		this.renderBody(graphics, font, mouseX, mouseY, opacity);
		this.renderFooter(graphics, font, mouseX, mouseY, opacity);

		graphics.pose().popMatrix();
	}

	private void renderHeader(GuiGraphicsExtractor graphics, Font font, long now, float opacity) {
		if (this.conflict.isDuel()) {
			VersusBanner.render(graphics, font, this.x + 8, this.y + 8, this.width - 16, 58,
				this.conflict.contenders().get(0), this.conflict.contenders().get(1), now, opacity);
		} else {
			graphics.text(font, this.conflict.targetLabel(), this.x + 12, this.y + 12,
				Theme.withAlpha(Theme.TEXT, opacity), true);
			graphics.text(font, this.conflict.modIds().size() + " mods implicados", this.x + 12, this.y + 24,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
		}
		Draw.divider(graphics, this.x + 10, this.bodyTop - 6, this.width - 20, opacity);
	}

	private void renderBody(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		this.actions.clear();

		int textX = this.x + 12;
		int maxWidth = this.width - 24;
		graphics.enableScissor(this.x + 2, this.bodyTop, this.x + this.width - 2, this.bodyBottom);
		int cursorY = this.bodyTop - this.scroll.intValue();

		// ---- what is being touched ----
		cursorY = Draw.sectionHeader(graphics, font, "Que se esta tocando", textX, cursorY, maxWidth,
			Theme.ACCENT_BRIGHT, opacity);

		String simpleClass = this.conflict.targetClass().substring(
			this.conflict.targetClass().lastIndexOf('/') + 1);
		int badgeWidth = Draw.badge(graphics, font, ClassGlossary.domainOf(this.conflict.targetClass()),
			textX, cursorY, Theme.INFO, opacity);
		Draw.clippedText(graphics, font, simpleClass, textX + badgeWidth + 6, cursorY,
			maxWidth - badgeWidth - 6, Theme.withAlpha(Theme.TEXT, opacity), false);
		cursorY += 12;

		Draw.clippedText(graphics, font, this.conflict.targetClass().replace('/', '.'), textX, cursorY,
			maxWidth, Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.85F), false);
		cursorY += 12;

		// The exact signature, straight from the compiled class.
		if (this.target.exists()) {
			Draw.roundedRect(graphics, textX, cursorY - 1, maxWidth, 12, 2,
				Theme.argb(Math.round(60 * opacity), 0x120A1E));
			Draw.clippedText(graphics, font, this.target.signature(), textX + 4, cursorY + 1,
				maxWidth - 8, Theme.withAlpha(0xFF9CD2FF, opacity), false);
			cursorY += 14;
			Draw.clippedText(graphics, font, "descriptor JVM: " + this.target.descriptor(), textX, cursorY,
				maxWidth, Theme.withAlpha(Theme.TEXT_DIM, opacity * 0.8F), false);
			cursorY += 11;
			if (this.overloads.size() > 1) {
				Draw.clippedText(graphics, font,
					this.overloads.size() + " sobrecargas con ese nombre en la clase",
					textX, cursorY, maxWidth, Theme.withAlpha(Theme.WARN, opacity), false);
				cursorY += 11;
			}
		} else {
			// A mixin pointing at a method that is not there would fail to apply.
			cursorY = Draw.wrappedText(graphics, font,
				"No se encontro el metodo '" + this.conflict.plainMethodName() + "' en esa clase. "
					+ "Puede ser un selector con comodines, o un mixin que ya no aplica en esta version.",
				textX, cursorY, maxWidth, 3, Theme.WARN, opacity);
		}
		cursorY += 4;

		cursorY = Draw.wrappedText(graphics, font, ClassGlossary.explain(this.conflict.targetClass()),
			textX, cursorY, maxWidth, 4, Theme.TEXT_MUTED, opacity);
		String methodNote = ClassGlossary.describeMethod(this.conflict.plainMethodName());
		if (methodNote != null) {
			cursorY = Draw.wrappedText(graphics, font, methodNote, textX, cursorY, maxWidth, 3,
				Theme.TEXT_DIM, opacity);
		}
		cursorY += 6;

		// ---- who does what ----
		cursorY = Draw.sectionHeader(graphics, font, "Quien hace que", textX, cursorY, maxWidth,
			Theme.ACCENT_BRIGHT, opacity);

		Conflict.Contender winner = this.conflict.predictedWinner();
		for (Conflict.Contender contender : this.conflict.contenders()) {
			boolean isWinner = winner != null && winner.modId().equals(contender.modId())
				&& winner.kind() == contender.kind();

			int rowHeight = 30;
			Draw.roundedRect(graphics, textX, cursorY, maxWidth, rowHeight - 3, 3,
				Theme.withAlpha(Theme.PANEL, opacity));
			if (isWinner) {
				graphics.fill(textX, cursorY, textX + 2, cursorY + rowHeight - 3,
					Theme.withAlpha(Theme.GOOD, opacity));
			}

			ModIcons.draw(graphics, font, contender.modId(), contender.displayName(),
				textX + 6, cursorY + 4, 14, Theme.ACCENT, opacity);

			Draw.clippedText(graphics, font, contender.displayName(), textX + 25, cursorY + 3,
				maxWidth - 120, Theme.withAlpha(Theme.TEXT, opacity), false);

			String kindLabel = contender.kind().label();
			int kindColor = switch (contender.kind().severity()) {
				case EXCLUSIVE -> Theme.DANGER;
				case COOPERATIVE -> Theme.WARN;
				case ADDITIVE -> Theme.GOOD;
			};
			int kindWidth = Draw.badge(graphics, font, kindLabel, textX + 25, cursorY + 14, kindColor, opacity);
			Draw.clippedText(graphics, font, "prioridad " + contender.priority(),
				textX + 25 + kindWidth + 6, cursorY + 15, 90,
				Theme.withAlpha(Theme.TEXT_DIM, opacity), false);

			// Offer the mod's own config, which is the only supported way to turn a mixin off.
			boolean hasConfig = hasConfig(contender.modId());
			if (hasConfig) {
				int actionWidth = 62;
				int actionX = textX + maxWidth - actionWidth - 5;
				boolean hovered = Draw.inside(mouseX, mouseY, actionX, cursorY + 6, actionWidth, 14);
				Draw.roundedRect(graphics, actionX, cursorY + 6, actionWidth, 14, 3,
					Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
				graphics.centeredText(font, "Ajustes", actionX + actionWidth / 2, cursorY + 9,
					Theme.withAlpha(Theme.TEXT, opacity));
				this.actions.add(new ActionSpot(actionX, cursorY + 6, actionWidth, 14, contender.modId()));
				if (hovered) {
					Tooltip.request("Abrir la configuracion de " + contender.displayName(),
						"Muchos mods permiten desactivar mixins concretos desde su propio archivo de "
							+ "configuracion. Es la unica forma soportada de apartar uno sin tocar el jar.",
						mouseX, mouseY);
				}
			}

			if (Draw.inside(mouseX, mouseY, textX, cursorY, maxWidth - 70, rowHeight - 3)) {
				Tooltip.request(kindLabel + " en " + contender.displayName(),
					injectionExplanation(contender.kind()) + "  Mixin: " + shortMixin(contender.mixinClass()),
					mouseX, mouseY);
			}
			cursorY += rowHeight;
		}
		cursorY += 4;

		// ---- verdict ----
		cursorY = Draw.sectionHeader(graphics, font, "Quien gana", textX, cursorY, maxWidth,
			this.conflict.isTie() ? Theme.DANGER : Theme.ACCENT_BRIGHT, opacity);

		String verdict;
		int verdictColor;
		if (this.conflict.isTie()) {
			verdict = "Empate de prioridad. Mixin no garantiza cual gana: depende del orden de carga, "
				+ "asi que puede cambiar entre arranques. Es el peor caso, porque el fallo no es reproducible.";
			verdictColor = Theme.DANGER;
		} else if (winner != null) {
			verdict = winner.displayName() + " gana con prioridad " + winner.priority()
				+ ". El resto de inyecciones exclusivas sobre este punto no se aplicaran.";
			verdictColor = Theme.WARN;
		} else {
			verdict = "Nadie reclama el metodo en exclusiva: las inyecciones se encadenan y conviven.";
			verdictColor = Theme.GOOD;
		}
		cursorY = Draw.wrappedText(graphics, font, verdict, textX, cursorY, maxWidth, 4, verdictColor, opacity);
		cursorY += 4;

		cursorY = Draw.wrappedText(graphics, font, this.conflict.advice(), textX, cursorY, maxWidth, 3,
			Theme.TEXT_MUTED, opacity);
		cursorY += 6;

		// ---- what can and cannot be done ----
		cursorY = Draw.sectionHeader(graphics, font, "Aislar el conflicto", textX, cursorY, maxWidth,
			Theme.ACCENT_BRIGHT, opacity);
		cursorY = Draw.wrappedText(graphics, font,
			"No se puede forzar un ganador desde fuera: la prioridad vive dentro del jar de cada mod y "
				+ "reescribirlo romperia su firma. Lo que si funciona es el interruptor que el propio mod "
				+ "expone en su configuracion — Sodium y Lithium, por ejemplo, permiten desactivar mixins "
				+ "concretos. Usa 'Ajustes' arriba para abrirlo.",
			textX, cursorY, maxWidth, 6, Theme.TEXT_DIM, opacity);

		graphics.disableScissor();

		int content = cursorY + this.scroll.intValue() - this.bodyTop;
		this.maxScroll = Math.max(0, content - (this.bodyBottom - this.bodyTop) + 10);
		this.scroll.set(Mth.clamp(this.scroll.target(), 0, this.maxScroll));
	}

	private static String injectionExplanation(MixinTarget.Kind kind) {
		return switch (kind) {
			case OVERWRITE -> "Reemplaza el cuerpo del metodo entero. Nada mas puede convivir con esto.";
			case REDIRECT -> "Sustituye una llamada concreta dentro del metodo. Solo un mod puede hacerlo por llamada.";
			case WRAP_OPERATION -> "Envuelve una llamada de forma cooperativa: varios pueden encadenarse.";
			case WRAP_WITH_CONDITION -> "Salta una llamada bajo condicion. Cooperativo, pero el orden importa.";
			case MODIFY_CONSTANT -> "Cambia un valor constante del metodo. Dos mods cambiando el mismo se contradicen.";
			case MODIFY_ARG -> "Reescribe un argumento antes de la llamada.";
			case MODIFY_VARIABLE -> "Reescribe una variable local.";
			case MODIFY_RETURN -> "Reescribe el valor devuelto; se encadena limpio.";
			case INJECT -> "Anade codigo en un punto del metodo. Varios mods pueden hacerlo a la vez sin problema.";
		};
	}

	private static String shortMixin(String mixinClass) {
		int lastSlash = mixinClass.lastIndexOf('/');
		return lastSlash >= 0 ? mixinClass.substring(lastSlash + 1) : mixinClass;
	}

	private static boolean hasConfig(String modId) {
		for (InstalledMod mod : ModScanner.scan()) {
			if (mod.id().equals(modId)) {
				return !ModConfigLocator.filesFor(mod).isEmpty();
			}
		}
		return false;
	}

	private void renderFooter(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float opacity) {
		boolean hovered = Draw.inside(mouseX, mouseY, this.closeX, this.closeY, this.closeWidth, this.closeHeight);
		Draw.roundedRect(graphics, this.closeX, this.closeY, this.closeWidth, this.closeHeight, 4,
			Theme.withAlpha(hovered ? Theme.ACCENT_BRIGHT : Theme.BORDER, opacity));
		Draw.roundedRect(graphics, this.closeX + 1, this.closeY + 1, this.closeWidth - 2, this.closeHeight - 2, 3,
			Theme.withAlpha(hovered ? Theme.PANEL_HOVER : Theme.PANEL, opacity));
		graphics.centeredText(font, "Cerrar", this.closeX + this.closeWidth / 2,
			this.closeY + (this.closeHeight - 8) / 2, Theme.withAlpha(Theme.TEXT, opacity));
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
		if (Draw.inside(mouseX, mouseY, this.closeX, this.closeY, this.closeWidth, this.closeHeight)) {
			this.closed = true;
			return true;
		}
		for (ActionSpot spot : this.actions) {
			if (Draw.inside(mouseX, mouseY, spot.x(), spot.y(), spot.width(), spot.height())) {
				for (InstalledMod mod : ModScanner.scan()) {
					if (mod.id().equals(spot.modId())) {
						this.successor = new ConfigBrowserDialog(mod);
						this.closed = true;
						return true;
					}
				}
			}
		}
		if (!Draw.inside(mouseX, mouseY, this.x, this.y, this.width, this.height)) {
			this.closed = true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.maxScroll > 0) {
			this.scroll.set(Mth.clamp(this.scroll.target() - (float) amount * 16, 0, this.maxScroll));
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			this.closed = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public Dialog successor() {
		return this.successor;
	}
}

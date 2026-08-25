package dev.zymekoh.optimatch.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Minimal markdown renderer for the README Modrinth returns in a project's {@code body}.
 *
 * <p>This is deliberately not a full markdown implementation — mod READMEs are full of badges,
 * HTML and images that would be noise in a game menu. Headings, lists, quotes and code survive;
 * images and raw HTML are dropped, and links keep their text.
 */
public final class MarkdownView {
	private final List<Block> blocks = new ArrayList<>();
	private List<Line> wrapped = List.of();
	private int wrappedWidth = -1;

	public MarkdownView(String markdown) {
		parse(markdown == null ? "" : markdown);
	}

	private enum Style {
		H1, H2, BULLET, QUOTE, CODE, TEXT, BLANK
	}

	private record Block(Style style, String text) {
	}

	private record Line(Style style, String text) {
	}

	private void parse(String markdown) {
		boolean inCodeFence = false;

		for (String raw : markdown.split("\r?\n")) {
			String line = raw.strip();

			if (line.startsWith("```")) {
				inCodeFence = !inCodeFence;
				continue;
			}
			if (inCodeFence) {
				this.blocks.add(new Block(Style.CODE, raw));
				continue;
			}
			if (line.isEmpty()) {
				this.blocks.add(new Block(Style.BLANK, ""));
				continue;
			}
			// Badge rows and bare HTML carry nothing useful once the images are gone.
			if (line.startsWith("<") || line.startsWith("![")) {
				continue;
			}

			if (line.startsWith("###")) {
				this.blocks.add(new Block(Style.H2, clean(line.replaceFirst("^#+\\s*", ""))));
			} else if (line.startsWith("##")) {
				this.blocks.add(new Block(Style.H2, clean(line.replaceFirst("^#+\\s*", ""))));
			} else if (line.startsWith("#")) {
				this.blocks.add(new Block(Style.H1, clean(line.replaceFirst("^#+\\s*", ""))));
			} else if (line.startsWith(">")) {
				this.blocks.add(new Block(Style.QUOTE, clean(line.replaceFirst("^>\\s*", ""))));
			} else if (line.matches("^[-*+]\\s+.*")) {
				this.blocks.add(new Block(Style.BULLET, clean(line.replaceFirst("^[-*+]\\s+", ""))));
			} else if (line.matches("^\\d+\\.\\s+.*")) {
				this.blocks.add(new Block(Style.BULLET, clean(line.replaceFirst("^\\d+\\.\\s+", ""))));
			} else if (line.matches("^[-=_]{3,}$")) {
				this.blocks.add(new Block(Style.BLANK, ""));
			} else {
				String cleaned = clean(line);
				if (!cleaned.isBlank()) {
					this.blocks.add(new Block(Style.TEXT, cleaned));
				}
			}
		}
	}

	/** Strips inline markup that has no meaning without rich text. */
	private static String clean(String text) {
		String result = text;
		result = result.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");     // images
		result = result.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");  // links keep their label
		result = result.replaceAll("[*_]{1,3}([^*_]+)[*_]{1,3}", "$1");
		result = result.replaceAll("`([^`]*)`", "$1");
		result = result.replaceAll("<[^>]+>", "");
		return result.strip();
	}

	/** Re-wraps to {@code width}; cached until the width changes. */
	private void wrap(Font font, int width) {
		if (width == this.wrappedWidth) {
			return;
		}
		this.wrappedWidth = width;

		List<Line> lines = new ArrayList<>();
		for (Block block : this.blocks) {
			if (block.style() == Style.BLANK) {
				lines.add(new Line(Style.BLANK, ""));
				continue;
			}
			int indent = block.style() == Style.BULLET || block.style() == Style.QUOTE ? 8 : 0;
			for (String piece : wrapText(font, block.text(), Math.max(20, width - indent))) {
				lines.add(new Line(block.style(), piece));
			}
		}
		this.wrapped = List.copyOf(lines);
	}

	private static List<String> wrapText(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		String remaining = text;
		// Guard against pathological input: never loop more than the character count.
		int guard = text.length() + 2;
		while (!remaining.isEmpty() && guard-- > 0) {
			if (font.width(remaining) <= width) {
				out.add(remaining);
				break;
			}
			String head = font.plainSubstrByWidth(remaining, width);
			if (head.isEmpty()) {
				out.add(remaining);
				break;
			}
			int breakAt = head.lastIndexOf(' ');
			if (breakAt <= 0) {
				breakAt = head.length();
			}
			out.add(remaining.substring(0, breakAt).strip());
			remaining = remaining.substring(breakAt).strip();
		}
		return out;
	}

	public int contentHeight(Font font, int width) {
		wrap(font, width);
		int height = 0;
		for (Line line : this.wrapped) {
			height += lineHeight(line.style());
		}
		return height;
	}

	private static int lineHeight(Style style) {
		return switch (style) {
			case H1 -> 16;
			case H2 -> 14;
			case BLANK -> 5;
			default -> 10;
		};
	}

	/**
	 * Draws the document. The caller is responsible for the scissor rectangle and for offsetting
	 * {@code y} by the current scroll.
	 */
	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int clipTop,
					   int clipBottom, float opacity) {
		wrap(font, width);
		int cursorY = y;

		for (Line line : this.wrapped) {
			int height = lineHeight(line.style());
			if (cursorY + height >= clipTop && cursorY <= clipBottom) {
				switch (line.style()) {
					case H1 -> graphics.text(font, line.text(), x, cursorY + 3,
						Theme.withAlpha(Theme.ACCENT_BRIGHT, opacity), true);
					case H2 -> graphics.text(font, line.text(), x, cursorY + 2,
						Theme.withAlpha(Theme.ACCENT, opacity), false);
					case BULLET -> {
						graphics.text(font, "•", x, cursorY, Theme.withAlpha(Theme.ACCENT_DIM, opacity), false);
						graphics.text(font, line.text(), x + 8, cursorY, Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
					}
					case QUOTE -> {
						graphics.fill(x, cursorY, x + 2, cursorY + height, Theme.withAlpha(Theme.ACCENT_DIM, opacity));
						graphics.text(font, line.text(), x + 8, cursorY, Theme.withAlpha(Theme.TEXT_DIM, opacity), false);
					}
					case CODE -> {
						graphics.fill(x, cursorY - 1, x + width, cursorY + height - 1,
							Theme.argb(Math.round(70 * opacity), 0x120A1E));
						graphics.text(font, line.text(), x + 3, cursorY, Theme.withAlpha(Theme.INFO, opacity), false);
					}
					case TEXT -> graphics.text(font, line.text(), x, cursorY,
						Theme.withAlpha(Theme.TEXT_MUTED, opacity), false);
					case BLANK -> {
					}
				}
			}
			cursorY += height;
		}
	}

	public boolean isEmpty() {
		return this.blocks.isEmpty();
	}
}

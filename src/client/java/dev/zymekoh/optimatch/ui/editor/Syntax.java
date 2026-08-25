package dev.zymekoh.optimatch.ui.editor;

import dev.zymekoh.optimatch.config.EditableFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Token colouring for the config formats mods actually ship: JSON, TOML, properties and YAML.
 *
 * <p>A line is split into coloured spans rather than parsed properly — a config being edited is
 * usually mid-keystroke and invalid, and a real parser would just give up. Scanning character by
 * character always produces something reasonable.
 */
public final class Syntax {
	// Palette in the spirit of a dark editor theme, tuned to the mod's purple.
	public static final int KEY = 0xFF9CD2FF;
	public static final int STRING = 0xFFC3E88D;
	public static final int NUMBER = 0xFFF78C6C;
	public static final int BOOLEAN = 0xFFC792EA;
	public static final int COMMENT = 0xFF6B7A8F;
	public static final int PUNCTUATION = 0xFF8B9AAF;
	public static final int SECTION = 0xFFFFCB6B;
	public static final int PLAIN = 0xFFD8DEE9;

	private Syntax() {
	}

	/** A run of characters sharing one colour. */
	public record Span(String text, int color) {
	}

	public static List<Span> highlight(String line, EditableFile.Format format) {
		List<Span> spans = new ArrayList<>();
		if (line == null || line.isEmpty()) {
			return spans;
		}

		String trimmed = line.stripLeading();

		// Whole-line cases first.
		if (isComment(trimmed, format)) {
			spans.add(new Span(line, COMMENT));
			return spans;
		}
		if ((format == EditableFile.Format.TOML || format == EditableFile.Format.PLAIN)
			&& trimmed.startsWith("[") && trimmed.endsWith("]")) {
			spans.add(new Span(line, SECTION));
			return spans;
		}

		if (format == EditableFile.Format.PROPERTIES) {
			return highlightProperties(line);
		}
		return highlightStructured(line);
	}

	private static boolean isComment(String trimmed, EditableFile.Format format) {
		if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
			return true;
		}
		return format == EditableFile.Format.JSON && trimmed.startsWith("/*");
	}

	/** {@code key=value}: the key up to the first separator, then the value. */
	private static List<Span> highlightProperties(String line) {
		List<Span> spans = new ArrayList<>();
		int separator = indexOfAny(line, '=', ':');
		if (separator < 0) {
			spans.add(new Span(line, PLAIN));
			return spans;
		}
		spans.add(new Span(line.substring(0, separator), KEY));
		spans.add(new Span(String.valueOf(line.charAt(separator)), PUNCTUATION));

		String value = line.substring(separator + 1);
		spans.add(new Span(value, colorForBareValue(value.strip())));
		return spans;
	}

	/** JSON, TOML and YAML share enough shape to scan the same way. */
	private static List<Span> highlightStructured(String line) {
		List<Span> spans = new ArrayList<>();
		StringBuilder pending = new StringBuilder();
		boolean inString = false;
		boolean seenColon = false;
		char quote = '"';

		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);

			if (inString) {
				pending.append(character);
				boolean escaped = index > 0 && line.charAt(index - 1) == '\\';
				if (character == quote && !escaped) {
					// A quoted run before the colon is a key; after it, a value.
					spans.add(new Span(pending.toString(), seenColon ? STRING : KEY));
					pending.setLength(0);
					inString = false;
				}
				continue;
			}

			if (character == '"' || character == '\'') {
				flushBare(spans, pending);
				inString = true;
				quote = character;
				pending.append(character);
				continue;
			}

			if (character == ':' || character == '=') {
				flushBare(spans, pending);
				spans.add(new Span(String.valueOf(character), PUNCTUATION));
				seenColon = true;
				continue;
			}

			if (character == ',' || character == '{' || character == '}' || character == '[' || character == ']') {
				flushBare(spans, pending);
				spans.add(new Span(String.valueOf(character), PUNCTUATION));
				continue;
			}

			pending.append(character);
		}

		if (inString) {
			// Unterminated string: still colour it, the file is probably mid-edit.
			spans.add(new Span(pending.toString(), STRING));
		} else {
			flushBare(spans, pending);
		}
		return spans;
	}

	private static void flushBare(List<Span> spans, StringBuilder pending) {
		if (pending.length() == 0) {
			return;
		}
		String text = pending.toString();
		spans.add(new Span(text, colorForBareValue(text.strip())));
		pending.setLength(0);
	}

	private static int colorForBareValue(String value) {
		if (value.isEmpty()) {
			return PLAIN;
		}
		if (value.equals("true") || value.equals("false") || value.equals("null")) {
			return BOOLEAN;
		}
		return isNumeric(value) ? NUMBER : PLAIN;
	}

	private static boolean isNumeric(String value) {
		boolean digitSeen = false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (Character.isDigit(character)) {
				digitSeen = true;
			} else if (character != '.' && character != '-' && character != '+' && character != 'e' && character != 'E') {
				return false;
			}
		}
		return digitSeen;
	}

	private static int indexOfAny(String value, char first, char second) {
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == first || character == second) {
				return index;
			}
		}
		return -1;
	}
}

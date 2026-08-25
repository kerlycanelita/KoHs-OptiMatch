package dev.zymekoh.optimatch.config;

import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * An open config file: its lines, the caret, and what has changed since it was opened.
 *
 * <p><b>Reset</b> restores the file to the text that was on disk when it was opened — it undoes
 * <em>your</em> edits, not the settings that were already there. Restoring a mod's defaults is a
 * different operation and deliberately not offered, because only the mod knows them.
 */
public final class ConfigDocument {
	/** Two spaces: matches how most mods write their JSON. */
	private static final String INDENT = "  ";

	private final EditableFile file;
	private final String originalText;
	private final List<String> lines = new ArrayList<>();

	private int caretLine;
	private int caretColumn;

	private ConfigDocument(EditableFile file, String text) {
		this.file = file;
		this.originalText = text;
		setText(text);
	}

	/** Reads the file, refusing anything oversized or binary. */
	public static ConfigDocument open(EditableFile file) throws IOException {
		if (file.sizeBytes() > ModConfigLocator.MAX_EDITABLE_BYTES) {
			throw new IOException("El archivo es demasiado grande para editarlo aqui ("
				+ file.sizeLabel() + ").");
		}

		byte[] raw = Files.readAllBytes(file.path());
		for (byte value : raw) {
			// A NUL byte means this is not text, and editing it would corrupt the file.
			if (value == 0) {
				throw new IOException("Este archivo no es de texto.");
			}
		}
		return new ConfigDocument(file, new String(raw, StandardCharsets.UTF_8));
	}

	private void setText(String text) {
		this.lines.clear();
		for (String line : text.split("\n", -1)) {
			this.lines.add(line.replace("\r", ""));
		}
		if (this.lines.isEmpty()) {
			this.lines.add("");
		}
		clampCaret();
	}

	public EditableFile file() {
		return this.file;
	}

	public List<String> lines() {
		return this.lines;
	}

	public int lineCount() {
		return this.lines.size();
	}

	public int caretLine() {
		return this.caretLine;
	}

	public int caretColumn() {
		return this.caretColumn;
	}

	public String text() {
		return String.join("\n", this.lines);
	}

	public boolean isDirty() {
		return !text().equals(this.originalText);
	}

	/** Discards every edit made since the file was opened. */
	public void reset() {
		setText(this.originalText);
		this.caretLine = 0;
		this.caretColumn = 0;
	}

	/**
	 * Writes the file. The first save leaves a {@code .optimatch.bak} next to it holding the content
	 * as it was before this tool ever touched it, so a bad edit is always recoverable.
	 */
	public void save() throws IOException {
		Path target = this.file.path();
		Path backup = target.resolveSibling(target.getFileName() + ".optimatch.bak");

		if (!Files.exists(backup)) {
			Files.writeString(backup, this.originalText, StandardCharsets.UTF_8);
		}

		// Write beside the target and move, so an interrupted save cannot truncate the real file.
		Path temporary = target.resolveSibling(target.getFileName() + ".optimatch.tmp");
		Files.writeString(temporary, text(), StandardCharsets.UTF_8);
		Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);

		OptiMatchClient.LOGGER.info("Saved config {} ({} lines)", this.file.relativePath(), this.lines.size());
	}

	public Path backupPath() {
		Path target = this.file.path();
		return target.resolveSibling(target.getFileName() + ".optimatch.bak");
	}

	// ---- editing -------------------------------------------------------------------------------

	public void insert(String content) {
		if (content == null || content.isEmpty()) {
			return;
		}
		for (int index = 0; index < content.length(); index++) {
			char character = content.charAt(index);
			if (character == '\n') {
				newline();
			} else if (character != '\r') {
				String line = this.lines.get(this.caretLine);
				this.lines.set(this.caretLine,
					line.substring(0, this.caretColumn) + character + line.substring(this.caretColumn));
				this.caretColumn++;
			}
		}
	}

	public void insertIndent() {
		insert(INDENT);
	}

	/** Enter, carrying the current line's leading whitespace down with it. */
	public void newline() {
		String line = this.lines.get(this.caretLine);
		String head = line.substring(0, this.caretColumn);
		String tail = line.substring(this.caretColumn);

		int leading = 0;
		while (leading < head.length() && (head.charAt(leading) == ' ' || head.charAt(leading) == '\t')) {
			leading++;
		}
		String indent = head.substring(0, leading);

		this.lines.set(this.caretLine, head);
		this.lines.add(this.caretLine + 1, indent + tail);
		this.caretLine++;
		this.caretColumn = indent.length();
	}

	public void backspace() {
		if (this.caretColumn > 0) {
			String line = this.lines.get(this.caretLine);
			this.lines.set(this.caretLine,
				line.substring(0, this.caretColumn - 1) + line.substring(this.caretColumn));
			this.caretColumn--;
		} else if (this.caretLine > 0) {
			String removed = this.lines.remove(this.caretLine);
			this.caretLine--;
			this.caretColumn = this.lines.get(this.caretLine).length();
			this.lines.set(this.caretLine, this.lines.get(this.caretLine) + removed);
		}
	}

	public void delete() {
		String line = this.lines.get(this.caretLine);
		if (this.caretColumn < line.length()) {
			this.lines.set(this.caretLine,
				line.substring(0, this.caretColumn) + line.substring(this.caretColumn + 1));
		} else if (this.caretLine < this.lines.size() - 1) {
			this.lines.set(this.caretLine, line + this.lines.remove(this.caretLine + 1));
		}
	}

	// ---- caret ---------------------------------------------------------------------------------

	public void moveCaret(int deltaLine, int deltaColumn) {
		if (deltaColumn != 0) {
			this.caretColumn += deltaColumn;
			if (this.caretColumn < 0) {
				// Wrap to the end of the previous line rather than sticking at column zero.
				if (this.caretLine > 0) {
					this.caretLine--;
					this.caretColumn = this.lines.get(this.caretLine).length();
				} else {
					this.caretColumn = 0;
				}
			} else if (this.caretColumn > this.lines.get(this.caretLine).length()) {
				if (this.caretLine < this.lines.size() - 1) {
					this.caretLine++;
					this.caretColumn = 0;
				} else {
					this.caretColumn = this.lines.get(this.caretLine).length();
				}
			}
		}
		if (deltaLine != 0) {
			this.caretLine += deltaLine;
			clampCaret();
		}
	}

	public void placeCaret(int line, int column) {
		this.caretLine = line;
		this.caretColumn = column;
		clampCaret();
	}

	public void caretToLineStart() {
		this.caretColumn = 0;
	}

	public void caretToLineEnd() {
		this.caretColumn = this.lines.get(this.caretLine).length();
	}

	private void clampCaret() {
		this.caretLine = Math.max(0, Math.min(this.caretLine, this.lines.size() - 1));
		this.caretColumn = Math.max(0, Math.min(this.caretColumn, this.lines.get(this.caretLine).length()));
	}
}

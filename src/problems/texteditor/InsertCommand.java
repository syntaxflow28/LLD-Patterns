package problems.texteditor;

import java.util.Objects;

/**
 * Insert text at a position; undo deletes exactly what was inserted.
 *
 * <p>The interesting method is {@link #mergedWith}. Without coalescing, typing "hello" pushes five
 * commands and the user has to press Ctrl+Z five times to remove a word they typed in one motion.
 * Every editor merges consecutive typing into one undo unit, and the rule is small enough to state:
 * merge when the next insert starts exactly where this one ended, and both are short, and no
 * navigation or undo happened in between.
 *
 * <p>The instances stay immutable - merging produces a new command rather than mutating one that is
 * already on the undo stack. Mutating a command that has already executed is how redo starts undoing
 * the wrong range.
 */
public final class InsertCommand implements Command {

    private final Document document;
    private final int position;
    private final String value;

    public InsertCommand(Document document, int position, String value) {
        this.document = Objects.requireNonNull(document, "document");
        this.position = position;
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public void execute() {
        document.insert(position, value);
    }

    @Override
    public void undo() {
        document.delete(position, value.length());
    }

    @Override
    public String description() {
        return "Insert \"" + value + "\" at " + position;
    }

    /** @return the two inserts as one, or null when they are not contiguous typing */
    public InsertCommand mergedWith(InsertCommand next) {
        boolean contiguous = next.document == document && next.position == position + value.length();
        boolean typing = next.value.length() == 1 && next.value.charAt(0) != '\n';
        return contiguous && typing ? new InsertCommand(document, position, value + next.value) : null;
    }

    public int position() {
        return position;
    }

    public String value() {
        return value;
    }
}

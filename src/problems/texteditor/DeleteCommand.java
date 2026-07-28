package problems.texteditor;

import java.util.Objects;

/**
 * Delete a range; undo re-inserts what was removed.
 *
 * <p><b>The one thing candidates get wrong here.</b> The deleted text is captured inside
 * {@link #execute()}, not in the constructor. A command constructed as "delete 5 chars at 12" does
 * not yet know <em>which</em> 5 characters those are, and by the time undo runs they no longer exist
 * anywhere. Capture-on-execute is what makes the inverse recoverable.
 *
 * <p>It also means the field cannot be final and the command is not reusable across executions. That
 * is fine and worth saying: this is a command instance per edit, not a shared handler.
 */
public final class DeleteCommand implements Command {

    private final Document document;
    private final int position;
    private final int length;

    /** Captured at execute time. Null until then, which is exactly why undo before execute is a bug. */
    private String removed;

    public DeleteCommand(Document document, int position, int length) {
        this.document = Objects.requireNonNull(document, "document");
        this.position = position;
        this.length = length;
    }

    @Override
    public void execute() {
        removed = document.delete(position, length);
    }

    @Override
    public void undo() {
        if (removed == null) {
            throw new IllegalStateException("undo before execute: nothing was captured to restore");
        }
        document.insert(position, removed);
    }

    @Override
    public String description() {
        return "Delete " + length + " chars at " + position
                + (removed == null ? "" : " (\"" + removed + "\")");
    }

    /** Bytes this command must retain to stay undoable - used by the demo's memory comparison. */
    public int retainedChars() {
        return removed == null ? 0 : removed.length();
    }
}

package problems.texteditor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The undo/redo stacks, and the two invariants that make them correct.
 *
 * <p><b>Invariant 1 - a new edit clears the redo stack.</b> Once you undo back to a point and then
 * type something different, the future you undid never happened. Leaving those commands on the redo
 * stack means Ctrl+Y later replays an edit against a document that has moved underneath it: at best
 * text appears in the wrong place, at worst the position is past the end of the buffer and it throws.
 * Section 3 of the demo triggers exactly that. It is one line of code and it is the single most
 * common omission in this problem.
 *
 * <p><b>Invariant 2 - history is bounded.</b> An unbounded undo stack on a long editing session is a
 * memory leak with a friendly name. Capping the depth and dropping from the <em>bottom</em> (the
 * oldest edit) keeps the recent history intact, which is the only part anyone uses.
 *
 * <p>An {@link ArrayDeque} serves as both: {@code push}/{@code pop} at the head for stack behaviour,
 * {@code removeLast} at the tail to evict the oldest. A {@code Stack} or a {@code LinkedList} would
 * work; {@code ArrayDeque} is the one to name because it does both ends in O(1) without the
 * synchronisation {@code Stack} still carries.
 */
public final class CommandHistory {

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final int maxDepth;
    private final boolean coalesceTyping;

    public CommandHistory(int maxDepth, boolean coalesceTyping) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1");
        }
        this.maxDepth = maxDepth;
        this.coalesceTyping = coalesceTyping;
    }

    /** Executes a command and records it as one undo step. */
    public void run(Command command) {
        command.execute();

        // Invariant 1. Do this even when the command merges into the previous one.
        redoStack.clear();

        if (coalesceTyping
                && command instanceof InsertCommand next
                && undoStack.peek() instanceof InsertCommand previous) {
            InsertCommand merged = previous.mergedWith(next);
            if (merged != null) {
                undoStack.pop();
                undoStack.push(merged);
                return;
            }
        }

        undoStack.push(command);

        // Invariant 2. Evict the oldest, never the newest.
        while (undoStack.size() > maxDepth) {
            undoStack.removeLast();
        }
    }

    public boolean undo() {
        Command command = undoStack.poll();
        if (command == null) {
            return false;
        }
        command.undo();
        redoStack.push(command);
        return true;
    }

    public boolean redo() {
        Command command = redoStack.poll();
        if (command == null) {
            return false;
        }
        command.execute();
        undoStack.push(command);
        return true;
    }

    public Optional<String> undoLabel() {
        return Optional.ofNullable(undoStack.peek()).map(Command::description);
    }

    public Optional<String> redoLabel() {
        return Optional.ofNullable(redoStack.peek()).map(Command::description);
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    /** Newest first, for display. */
    public List<String> undoHistory() {
        List<String> labels = new ArrayList<>(undoStack.size());
        for (Command command : undoStack) {
            labels.add(command.description());
        }
        return labels;
    }
}

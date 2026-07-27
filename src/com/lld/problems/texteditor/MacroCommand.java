package com.lld.problems.texteditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * COMPOSITE over COMMAND - many edits that undo as one.
 *
 * <p>"Replace all" touches 40 places. Nobody wants 40 undo steps for one menu click, and neither does
 * a user who ran a formatter over a whole file. Grouping them into a single command is four lines and
 * it is the difference between a usable editor and an annoying one.
 *
 * <p><b>The detail that is graded: undo runs in reverse order.</b> Forward edits shift the positions
 * of later ones, so undoing first-to-last unwinds them against positions that have already moved and
 * silently corrupts the document. Reverse order restores each edit into exactly the document state it
 * was applied to.
 *
 * <p>Because it implements {@link Command}, nothing else in the system learns a new type - the
 * history stack, the undo key binding and the menu label all work unchanged. That is the entire
 * argument for Composite in one sentence.
 */
public final class MacroCommand implements Command {

    private final String label;
    private final List<Command> steps;

    public MacroCommand(String label, List<Command> steps) {
        this.label = Objects.requireNonNull(label, "label");
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("a macro needs at least one step");
        }
    }

    @Override
    public void execute() {
        // If a later step throws, the earlier ones have already applied. Rolling back here keeps the
        // macro atomic; an editor that skips this leaves the document half-formatted.
        List<Command> applied = new ArrayList<>(steps.size());
        try {
            for (Command step : steps) {
                step.execute();
                applied.add(step);
            }
        } catch (RuntimeException failure) {
            for (int i = applied.size() - 1; i >= 0; i--) {
                applied.get(i).undo();
            }
            throw failure;
        }
    }

    @Override
    public void undo() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            steps.get(i).undo();
        }
    }

    @Override
    public String description() {
        return label + " (" + steps.size() + " edits)";
    }

    public int size() {
        return steps.size();
    }
}

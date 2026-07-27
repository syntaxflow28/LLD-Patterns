package com.lld.problems.texteditor;

/**
 * COMMAND - an edit that knows how to undo itself.
 *
 * <p><b>Why Command and not Memento.</b> Both give you undo. Memento snapshots the whole document
 * before each edit and restores it; Command stores the <em>inverse</em> of the edit. On a document of
 * any real size the difference is not stylistic:
 *
 * <ul>
 *   <li>Memento: every undo step costs O(document). A 1 MB file and 100 undo levels is 100 MB.</li>
 *   <li>Command: every undo step costs O(edit). Typing a character stores a character.</li>
 * </ul>
 *
 * <p>Section 4 of the demo measures exactly this rather than asserting it.
 *
 * <p><b>When Memento wins anyway</b>, and say this so the choice sounds reasoned rather than
 * memorised: when the inverse is hard or impossible to express. "Apply this filter to the image",
 * "run this solver", anything lossy or non-deterministic - there is no small inverse to store, so you
 * snapshot. Real editors do both: Command for keystrokes, periodic snapshots so a 10,000 step undo
 * does not have to replay 10,000 inverses.
 *
 * <p><b>The invariant that makes undo work at all:</b> {@code undo()} must restore the exact state
 * that existed before {@code execute()}. That is why {@link DeleteCommand} captures the deleted text
 * <em>during</em> execute and not in its constructor - at construction time it only knows a position
 * and a length, and by the time you need to undo, the characters are gone.
 */
public interface Command {

    void execute();

    void undo();

    /** Human-readable label, so an editor can offer "Undo Typing" rather than "Undo". */
    String description();
}

package com.lld.problems.texteditor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Text editor with undo and redo - a 45 minute problem, run end to end.
 *
 * <p>Every section reproduces a failure rather than describing one, including the redo-stack bug and
 * the memory blow-up that makes Command beat Memento here.
 */
public final class TextEditorDemo {

    private TextEditorDemo() {
    }

    public static void main(String[] args) {
        budget();
        basics();
        redoStackInvariant();
        commandVersusMemento();
        boundedHistory();
        coalescingTyping();
        macroAsOneUndo();
        scopeNotes();
        System.out.println("\nDone.");
    }

    // ---------------------------------------------------------------- 1

    private static void budget() {
        section("1. The 45 minute budget");
        System.out.println("""
                    0-05  clarify: what is one undo step? bounded history? redo? macros?
                    05-12 model:   Command(execute/undo), Document, CommandHistory
                    12-22 code:    InsertCommand, DeleteCommand - capture the inverse
                    22-32 code:    undo/redo stacks + the clear-redo-on-new-edit rule
                    32-40 code:    bounded depth, and typing coalescing if time allows
                    40-45 talk:    Command vs Memento, and what a real editor stores

                  If you are running short, cut coalescing and macros. Do NOT cut clearing the
                  redo stack - that is the line the interviewer is watching for.\
                """);
    }

    // ---------------------------------------------------------------- 2

    private static void basics() {
        section("2. Type, undo, redo");

        Document document = new Document();
        CommandHistory history = new CommandHistory(50, false);

        history.run(new InsertCommand(document, 0, "Hello"));
        history.run(new InsertCommand(document, 5, " world"));
        history.run(new InsertCommand(document, 11, "!"));
        System.out.println("  after typing:  \"" + document + "\"");
        System.out.println("  undo would:    " + history.undoLabel().orElse("nothing"));

        history.undo();
        history.undo();
        System.out.println("  after 2 undos: \"" + document + "\"");
        System.out.println("  redo would:    " + history.redoLabel().orElse("nothing"));

        history.redo();
        System.out.println("  after 1 redo:  \"" + document + "\"");

        history.run(new DeleteCommand(document, 0, 6));
        System.out.println("  after delete:  \"" + document + "\"");
        history.undo();
        System.out.println("  after undo:    \"" + document + "\"  <- DeleteCommand kept the "
                + "characters it removed, so it could put them back");
    }

    // ---------------------------------------------------------------- 3

    private static void redoStackInvariant() {
        section("3. The redo-stack bug, reproduced");

        System.out.println("  Sequence: type 'Hello', type ' world', UNDO, then delete 4 chars,");
        System.out.println("  then press redo. The undone ' world' is stale - the document moved.\n");

        Document broken = new Document();
        BrokenHistory brokenHistory = new BrokenHistory();
        brokenHistory.run(new InsertCommand(broken, 0, "Hello"));
        brokenHistory.run(new InsertCommand(broken, 5, " world"));
        brokenHistory.undo();
        brokenHistory.run(new DeleteCommand(broken, 0, 4));
        System.out.println("  broken:  document is now \"" + broken + "\", redo stack still holds "
                + brokenHistory.redoDepth() + " command(s)");
        try {
            brokenHistory.redo();
            System.out.println("  broken:  redo produced \"" + broken + "\"  <- text resurrected in "
                    + "the wrong place");
        } catch (IndexOutOfBoundsException corrupted) {
            System.out.println("  broken:  redo threw " + corrupted.getClass().getSimpleName()
                    + ": " + corrupted.getMessage());
            System.out.println("           the stale command targets position 5 in a 1-char document");
        }

        Document fixed = new Document();
        CommandHistory history = new CommandHistory(50, false);
        history.run(new InsertCommand(fixed, 0, "Hello"));
        history.run(new InsertCommand(fixed, 5, " world"));
        history.undo();
        history.run(new DeleteCommand(fixed, 0, 4));
        System.out.println("\n  fixed:   document is now \"" + fixed + "\", redo stack holds "
                + history.redoDepth());
        System.out.println("  fixed:   redo() -> " + history.redo() + "  (nothing to redo, correctly)");

        System.out.println("""

                  The whole fix is one line at the top of run():  redoStack.clear();
                  Once you branch off the undone history, that future is gone. Editors that get
                  this wrong are the ones where Ctrl+Y occasionally pastes something you deleted
                  ten minutes ago into the middle of a word.\
                """);
    }

    /** Deliberately missing {@code redoStack.clear()}, so section 3 can show the consequence. */
    private static final class BrokenHistory {
        private final Deque<Command> undoStack = new ArrayDeque<>();
        private final Deque<Command> redoStack = new ArrayDeque<>();

        void run(Command command) {
            command.execute();
            undoStack.push(command);
            // BUG: the redo stack is never cleared here.
        }

        void undo() {
            Command command = undoStack.poll();
            if (command != null) {
                command.undo();
                redoStack.push(command);
            }
        }

        void redo() {
            Command command = redoStack.poll();
            if (command != null) {
                command.execute();
                undoStack.push(command);
            }
        }

        int redoDepth() {
            return redoStack.size();
        }
    }

    // ---------------------------------------------------------------- 4

    private static void commandVersusMemento() {
        section("4. Command against Memento, measured");

        int documentSize = 256 * 1024;
        int edits = 50;
        String filler = "x".repeat(documentSize);

        // Memento: snapshot the whole document before every edit.
        Document mementoDocument = new Document(filler);
        List<String> snapshots = new ArrayList<>(edits);
        long snapshotChars = 0;
        for (int i = 0; i < edits; i++) {
            String snapshot = mementoDocument.text();
            snapshots.add(snapshot);
            snapshotChars += snapshot.length();
            mementoDocument.insert(i, "edit-" + i);
        }

        // Command: store only the inverse of each edit.
        Document commandDocument = new Document(filler);
        CommandHistory history = new CommandHistory(edits, false);
        long inverseChars = 0;
        for (int i = 0; i < edits; i++) {
            String inserted = "edit-" + i;
            history.run(new InsertCommand(commandDocument, i, inserted));
            inverseChars += inserted.length();
        }

        System.out.printf("      %,d char document, %d edits, full undo history kept%n",
                documentSize, edits);
        System.out.printf("      Memento (snapshot per edit)   %,12d chars retained%n", snapshotChars);
        System.out.printf("      Command (inverse per edit)    %,12d chars retained%n", inverseChars);
        System.out.printf("      ratio                         %,12d x%n", snapshotChars / inverseChars);
        System.out.println("      same document either way: "
                + mementoDocument.text().equals(commandDocument.text()));
        System.out.println("      snapshots still referenced:  " + snapshots.size());

        System.out.println("""

                  That is the argument, and it is worth making with numbers rather than taste.
                  Memento's cost is O(document) per undo level; Command's is O(edit). Scale the
                  document up and the gap widens - the edits do not get bigger.

                  Memento is still the right call when the inverse is not expressible: a lossy
                  image filter, a non-deterministic transform, anything where 'what was there
                  before' cannot be reconstructed from the operation. Real editors use both,
                  snapshotting every N commands so a deep undo does not replay thousands of steps.\
                """);
    }

    // ---------------------------------------------------------------- 5

    private static void boundedHistory() {
        section("5. Bounded history - an unbounded undo stack is a leak");

        Document document = new Document();
        CommandHistory history = new CommandHistory(3, false);
        for (int i = 1; i <= 6; i++) {
            history.run(new InsertCommand(document, document.length(), "[" + i + "]"));
        }
        System.out.println("  after 6 edits: \"" + document + "\", undo depth " + history.undoDepth());
        System.out.println("  retained:      " + history.undoHistory());

        int undone = 0;
        while (history.undo()) {
            undone++;
        }
        System.out.println("  undid " + undone + " edits, document is now \"" + document + "\"");
        System.out.println("""

                  The oldest three edits are gone forever, and that is the intended trade. Evicting
                  from the newest end instead would silently break the next Ctrl+Z, which is the
                  only one anybody presses.\
                """);
    }

    // ---------------------------------------------------------------- 6

    private static void coalescingTyping() {
        section("6. Coalescing - typing a word is one undo, not five");

        Document naiveDocument = new Document();
        CommandHistory naive = new CommandHistory(50, false);
        type(naive, naiveDocument, "hello");
        System.out.println("  without coalescing: \"" + naiveDocument + "\", undo depth "
                + naive.undoDepth());
        naive.undo();
        System.out.println("    one Ctrl+Z ->     \"" + naiveDocument + "\"");

        Document mergedDocument = new Document();
        CommandHistory merging = new CommandHistory(50, true);
        type(merging, mergedDocument, "hello");
        System.out.println("  with coalescing:    \"" + mergedDocument + "\", undo depth "
                + merging.undoDepth());
        System.out.println("    undo label:       " + merging.undoLabel().orElse("nothing"));
        merging.undo();
        System.out.println("    one Ctrl+Z ->     \"" + mergedDocument + "\"");

        Document jumped = new Document();
        CommandHistory jumping = new CommandHistory(50, true);
        type(jumping, jumped, "hi");
        jumping.run(new InsertCommand(jumped, 0, "X"));
        System.out.println("  after moving the caret and typing elsewhere: undo depth "
                + jumping.undoDepth() + "  <- not merged, positions are not contiguous");

        System.out.println("""

                  Merge only when the next insert starts exactly where the last one ended. Real
                  editors add a time window and break the run on Enter, on caret movement and on
                  save. Mention those; the contiguity rule alone is enough code for the interview.\
                """);
    }

    private static void type(CommandHistory history, Document document, String word) {
        for (int i = 0; i < word.length(); i++) {
            history.run(new InsertCommand(document, document.length(), String.valueOf(word.charAt(i))));
        }
    }

    // ---------------------------------------------------------------- 7

    private static void macroAsOneUndo() {
        section("7. Replace-all as a single undo step");

        Document document = new Document("the cat sat on the cat mat, near another cat");
        CommandHistory history = new CommandHistory(50, false);
        System.out.println("  before:  \"" + document + "\"");

        MacroCommand replaceAll = replaceAll(document, "cat", "hamster");
        System.out.println("  macro:   " + replaceAll.description());
        history.run(replaceAll);
        System.out.println("  after:   \"" + document + "\"");
        System.out.println("  undo depth: " + history.undoDepth() + "  (not " + replaceAll.size() + ")");

        history.undo();
        System.out.println("  undo:    \"" + document + "\"");
        history.redo();
        System.out.println("  redo:    \"" + document + "\"");

        System.out.println("""

                  Two details earn the marks here. Undo walks the steps in REVERSE, because each
                  forward edit shifted the positions of the ones after it. And the replacement is
                  longer than the needle, so the step positions carry a running offset - build them
                  against the original text plus a cumulative delta, not against a moving target.\
                """);
    }

    private static MacroCommand replaceAll(Document document, String needle, String replacement) {
        List<Command> steps = new ArrayList<>();
        int delta = 0;
        for (int found = document.indexOf(needle, 0);
                found >= 0;
                found = document.indexOf(needle, found + needle.length())) {
            steps.add(new DeleteCommand(document, found + delta, needle.length()));
            steps.add(new InsertCommand(document, found + delta, replacement));
            delta += replacement.length() - needle.length();
        }
        return new MacroCommand("Replace \"" + needle + "\" with \"" + replacement + "\"", steps);
    }

    // ---------------------------------------------------------------- 8

    private static void scopeNotes() {
        section("8. What you would cut, and what you would say instead");

        System.out.println("""
                  Cut, and say you are cutting them:
                    - the buffer itself. StringBuilder is O(n) per edit; name gap buffer, piece
                      table or rope as the upgrade and move on. Writing one is a separate hour.
                    - selection, clipboard, cursor model, find-and-replace UI, syntax highlighting.
                    - persistence and crash recovery.
                    - collaborative editing. If it comes up, say OT or CRDT and say that undo in a
                      collaborative editor is a genuinely hard problem, not a bigger stack.

                  Build, because they are what is graded:
                    - Command with a real inverse, captured at execute time
                    - undo and redo stacks, with redo cleared on a new edit
                    - bounded history
                    - a macro that undoes as one unit

                  Follow-ups worth pre-empting out loud:
                    - 'Why not Memento?'        -> section 4, with the numbers.
                    - 'Multi-user editing?'     -> the inverse of a command is no longer valid once
                      someone else edits underneath it. That is where OT and CRDTs come from.
                    - 'Undo a save?'            -> not every action is undoable. Commands that
                      cross a system boundary need a compensating action, not an inverse.
                    - 'Group by time, not type?' -> the merge rule is policy; make it a predicate
                      and it becomes a Strategy the moment there is a second one.\
                """);
    }

    // ---------------------------------------------------------------- helpers

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}

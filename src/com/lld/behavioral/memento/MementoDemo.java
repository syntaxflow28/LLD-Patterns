package com.lld.behavioral.memento;

import java.util.ArrayDeque;
import java.util.Deque;

/*
 * MEMENTO — capture and externalise an object's internal state so it can be restored later,
 * WITHOUT violating encapsulation.
 *
 * Three roles:
 *   Originator — the object whose state we snapshot (Editor)
 *   Memento    — the opaque snapshot (EditorSnapshot); only the originator can read its guts
 *   Caretaker  — stores snapshots but never inspects them (History)
 *
 * When to use in LLD:
 *   - Undo/redo, checkpoints/save games, transaction rollback, "restore draft".
 *
 * Memento vs Command-undo: Command replays an inverse operation; Memento restores a full snapshot.
 * Memento is simpler but uses more memory.
 */

/** The memento: immutable, opaque to everyone but the Originator. */
final class EditorSnapshot {
    private final String content;      // private + no public getters -> encapsulation preserved
    private final int cursor;

    private EditorSnapshot(String content, int cursor) { this.content = content; this.cursor = cursor; }

    static EditorSnapshot of(String content, int cursor) { return new EditorSnapshot(content, cursor); }

    // Package-private accessors: only the Originator in this package restores from it.
    String content() { return content; }
    int cursor() { return cursor; }
}

/** Originator. */
class Editor {
    private String content = "";
    private int cursor = 0;

    void type(String text) { content += text; cursor = content.length(); }

    EditorSnapshot save()                     { return EditorSnapshot.of(content, cursor); }
    void restore(EditorSnapshot snapshot)     { this.content = snapshot.content(); this.cursor = snapshot.cursor(); }

    @Override public String toString() { return "\"" + content + "\" (cursor=" + cursor + ")"; }
}

/** Caretaker: manages the history stack but never looks inside a snapshot. */
class History {
    private final Deque<EditorSnapshot> stack = new ArrayDeque<>();
    void push(EditorSnapshot s) { stack.push(s); }
    EditorSnapshot pop()        { return stack.isEmpty() ? null : stack.pop(); }
}

public class MementoDemo {
    public static void main(String[] args) {
        Editor editor = new Editor();
        History history = new History();

        editor.type("Hello");
        history.push(editor.save());       // checkpoint 1

        editor.type(", World");
        history.push(editor.save());       // checkpoint 2

        editor.type("!!! oops typo");
        System.out.println("Now      : " + editor);

        editor.restore(history.pop());     // back to checkpoint 2
        System.out.println("Undo x1  : " + editor);

        editor.restore(history.pop());     // back to checkpoint 1
        System.out.println("Undo x2  : " + editor);
    }
}

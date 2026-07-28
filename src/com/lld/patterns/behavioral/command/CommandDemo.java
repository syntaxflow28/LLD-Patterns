package com.lld.patterns.behavioral.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * COMMAND — encapsulate a request as an object. This lets you parameterize clients with different
 * requests, queue or log them, and support undo/redo.
 *
 * When to use in LLD:
 *   - Undo/redo (editors), task queues/job schedulers, remote controls, transactional operations,
 *     macro recording.
 *
 * Here: a text editor with typing commands and undo support.
 */

interface Command {
    void execute();
    void undo();
}

/** Receiver: the object the commands act upon. */
class TextDocument {
    private final StringBuilder text = new StringBuilder();
    void append(String s) { text.append(s); }
    void deleteLast(int n) { text.delete(text.length() - n, text.length()); }
    @Override public String toString() { return text.toString(); }
}

class AppendTextCommand implements Command {
    private final TextDocument doc;
    private final String toAppend;

    AppendTextCommand(TextDocument doc, String toAppend) { this.doc = doc; this.toAppend = toAppend; }

    public void execute() { doc.append(toAppend); }
    public void undo()    { doc.deleteLast(toAppend.length()); } // knows how to reverse itself
}

/** Invoker: triggers commands and keeps a history stack for undo. */
class EditorInvoker {
    private final Deque<Command> history = new ArrayDeque<>();

    void run(Command c) { c.execute(); history.push(c); }

    void undo() {
        if (!history.isEmpty()) history.pop().undo();
    }
}

public class CommandDemo {
    public static void main(String[] args) {
        TextDocument doc = new TextDocument();
        EditorInvoker editor = new EditorInvoker();

        editor.run(new AppendTextCommand(doc, "Hello"));
        editor.run(new AppendTextCommand(doc, ", World"));
        System.out.println("After typing : " + doc);

        editor.undo();
        System.out.println("After undo    : " + doc);
    }
}

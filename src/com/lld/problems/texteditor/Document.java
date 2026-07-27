package com.lld.problems.texteditor;

/**
 * The receiver: the text buffer itself.
 *
 * <p>Deliberately dumb. It knows how to insert and delete and nothing about undo, history or
 * commands. That separation is the point of the Command pattern here - the document never grows an
 * {@code undo()} method, so adding a new kind of edit does not touch it.
 *
 * <p>A {@code StringBuilder} is O(n) per edit because it shifts the tail. Say that out loud, then say
 * what you would use if the interviewer pushes: a gap buffer (fast for edits clustered around one
 * cursor, which is how humans type), a piece table (what VS Code uses - edits are append-only and the
 * document is a list of spans), or a rope (fast for huge files and arbitrary splices). Do not build
 * one in 45 minutes; naming the right one is the whole answer.
 */
public final class Document {

    private final StringBuilder text;

    public Document() {
        this("");
    }

    public Document(String initial) {
        this.text = new StringBuilder(initial);
    }

    public void insert(int position, String value) {
        requirePosition(position, text.length());
        text.insert(position, value);
    }

    /** @return the removed text, which is exactly the inverse the caller needs to undo this */
    public String delete(int position, int length) {
        requirePosition(position, text.length());
        if (length < 0 || position + length > text.length()) {
            throw new IndexOutOfBoundsException(
                    "cannot delete " + length + " chars at " + position + " of " + text.length());
        }
        String removed = text.substring(position, position + length);
        text.delete(position, position + length);
        return removed;
    }

    public int indexOf(String needle, int from) {
        return text.indexOf(needle, from);
    }

    public int length() {
        return text.length();
    }

    public String text() {
        return text.toString();
    }

    @Override
    public String toString() {
        return text.toString();
    }

    private static void requirePosition(int position, int length) {
        if (position < 0 || position > length) {
            throw new IndexOutOfBoundsException("position " + position + " outside 0.." + length);
        }
    }
}

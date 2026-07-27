package com.lld.structural.composite;

import java.util.ArrayList;
import java.util.List;

/**
 * COMPOSITE — compose objects into tree structures and treat individual objects and compositions
 * uniformly through one interface.
 *
 * When to use in LLD:
 *   - Part-whole hierarchies: file systems (files + folders), org charts, UI component trees,
 *     nested menus. Client code shouldn't care if it holds a leaf or a branch.
 *
 * Here: files (leaf) and directories (composite) both implement FileSystemNode.
 */

interface FileSystemNode {
    int size();                 // uniform operation across leaves and composites
    void print(String indent);
}

/** Leaf. */
class FileNode implements FileSystemNode {
    private final String name;
    private final int bytes;

    FileNode(String name, int bytes) { this.name = name; this.bytes = bytes; }

    public int size() { return bytes; }
    public void print(String indent) { System.out.println(indent + "- " + name + " (" + bytes + "b)"); }
}

/** Composite: holds children that may themselves be files or directories. */
class DirectoryNode implements FileSystemNode {
    private final String name;
    private final List<FileSystemNode> children = new ArrayList<>();

    DirectoryNode(String name) { this.name = name; }

    DirectoryNode add(FileSystemNode node) { children.add(node); return this; }

    public int size() {
        int total = 0;
        for (FileSystemNode c : children) total += c.size();  // recurse uniformly
        return total;
    }

    public void print(String indent) {
        System.out.println(indent + "+ " + name + "/");
        for (FileSystemNode c : children) c.print(indent + "  ");
    }
}

public class CompositeDemo {
    public static void main(String[] args) {
        DirectoryNode root = new DirectoryNode("root");
        root.add(new FileNode("readme.md", 120));

        DirectoryNode src = new DirectoryNode("src");
        src.add(new FileNode("Main.java", 800));
        src.add(new FileNode("Util.java", 450));

        root.add(src);

        root.print("");
        System.out.println("Total size = " + root.size() + " bytes"); // works over the whole tree
    }
}

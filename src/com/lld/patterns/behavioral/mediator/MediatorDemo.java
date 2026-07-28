package com.lld.patterns.behavioral.mediator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MEDIATOR — define an object that encapsulates how a set of objects interact. Colleagues no
 * longer reference each other directly; they talk through the mediator.
 *
 * Turns an N-to-N web of dependencies into N-to-1.
 *
 * When to use in LLD:
 *   - Chat rooms, air-traffic control, auction houses, ride-matching (riders <-> drivers),
 *     complex UI dialogs where widgets enable/disable each other.
 *
 * Mediator vs Observer: Observer is a broadcast of state change; Mediator centralizes *coordination
 * logic* and can route/transform messages.
 */

interface ChatMediator {
    void register(User user);
    void send(String from, String to, String message);   // direct message
    void broadcast(String from, String message);          // room-wide
}

abstract class User {
    protected final String name;
    protected final ChatMediator mediator;

    User(String name, ChatMediator mediator) {
        this.name = name;
        this.mediator = mediator;
        mediator.register(this);
    }

    void send(String to, String msg)  { mediator.send(name, to, msg); }   // never touches other Users
    void shout(String msg)            { mediator.broadcast(name, msg); }
    abstract void receive(String from, String msg);
}

class ChatUser extends User {
    ChatUser(String name, ChatMediator mediator) { super(name, mediator); }
    void receive(String from, String msg) { System.out.println("[" + name + "] " + from + ": " + msg); }
}

/** The mediator owns all routing rules — the only place that knows the topology. */
class ChatRoom implements ChatMediator {
    private final Map<String, User> users = new HashMap<>();
    private final List<String> banned = new ArrayList<>();

    public void register(User user) { users.put(user.name, user); }

    void ban(String name) { banned.add(name); }

    public void send(String from, String to, String message) {
        if (banned.contains(from)) { System.out.println("(blocked message from " + from + ")"); return; }
        User target = users.get(to);
        if (target == null) { System.out.println("(no such user: " + to + ")"); return; }
        target.receive(from, message);
    }

    public void broadcast(String from, String message) {
        if (banned.contains(from)) { System.out.println("(blocked broadcast from " + from + ")"); return; }
        users.values().stream()
                .filter(u -> !u.name.equals(from))
                .forEach(u -> u.receive(from, message));
    }
}

public class MediatorDemo {
    public static void main(String[] args) {
        ChatRoom room = new ChatRoom();
        User alice = new ChatUser("Alice", room);
        User bob   = new ChatUser("Bob", room);
        User carol = new ChatUser("Carol", room);

        alice.shout("hello everyone");
        bob.send("Alice", "hey Alice");

        room.ban("Carol");
        carol.shout("spam spam spam");   // mediator enforces the rule centrally
    }
}

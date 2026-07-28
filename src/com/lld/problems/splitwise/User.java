package com.lld.problems.splitwise;

import java.util.Objects;

/** A person. Identity is the id, so this is a record with id-based equality for free. */
public record User(String id, String name, String email) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public String toString() {
        return name;
    }
}

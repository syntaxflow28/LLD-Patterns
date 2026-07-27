package com.lld.practical.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REPOSITORY — mediate between the domain and the data layer, exposing a collection-like interface
 * for aggregate roots. Business logic never sees SQL, HTTP, or file I/O.
 *
 * When to use in LLD:
 *   - Almost every system with persistence. It's the standard answer to "where does the DB fit in
 *     your design?" and it makes services trivially unit-testable with an in-memory implementation.
 *
 * Key benefit: the service depends on the Repository INTERFACE (DIP), so you can swap
 * InMemory -> JDBC -> Mongo without touching business logic.
 */

class User {
    private Long id;
    private final String email;
    private final String name;

    User(String email, String name) { this.email = email; this.name = name; }

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getEmail() { return email; }

    @Override public String toString() { return "User{" + id + ", " + name + ", " + email + "}"; }
}

/** Generic contract — the domain speaks this language, not the database's. */
interface Repository<T, ID> {
    T save(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean deleteById(ID id);
}

/** Domain-specific queries live on the specialised interface. */
interface UserRepository extends Repository<User, Long> {
    Optional<User> findByEmail(String email);
}

/** One implementation: in-memory (great for tests). A JdbcUserRepository would be a drop-in swap. */
class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public User save(User user) {
        if (user.getId() == null) user.setId(sequence.incrementAndGet());
        store.put(user.getId(), user);
        return user;
    }

    public Optional<User> findById(Long id) { return Optional.ofNullable(store.get(id)); }
    public List<User> findAll()             { return new ArrayList<>(store.values()); }
    public boolean deleteById(Long id)      { return store.remove(id) != null; }

    public Optional<User> findByEmail(String email) {
        return store.values().stream().filter(u -> u.getEmail().equals(email)).findFirst();
    }
}

/** Business logic depends only on the abstraction. */
class UserService {
    private final UserRepository repository;
    UserService(UserRepository repository) { this.repository = repository; }

    User register(String email, String name) {
        repository.findByEmail(email).ifPresent(u -> {
            throw new IllegalArgumentException("Email already registered: " + email);
        });
        return repository.save(new User(email, name));
    }
}

public class RepositoryDemo {
    public static void main(String[] args) {
        UserService service = new UserService(new InMemoryUserRepository());

        System.out.println(service.register("alice@x.com", "Alice"));
        System.out.println(service.register("bob@x.com", "Bob"));

        try {
            service.register("alice@x.com", "Alice Again");
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }
}

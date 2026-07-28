package com.lld.patterns.practical.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * RESULT / EITHER — make failure part of the return type instead of an invisible side channel.
 *
 * <p>A method signature like {@code User register(String email, String age)} is a lie: it says it
 * always returns a user. Whether it can fail, and how, is documented nowhere the compiler can see.
 * Callers find out in production.
 *
 * <p><b>The rule that settles the exceptions-versus-Result argument</b>, and the thing to say out
 * loud: <em>exceptions are for programmer errors and genuinely exceptional conditions; return values
 * are for expected business outcomes.</em> A disk failing is exceptional. An email address the user
 * typed wrong is not - it is the single most likely thing to happen, and modelling it as an
 * exception means routine control flow runs through try/catch.
 *
 * <p><b>Concrete reasons interviewers accept:</b>
 * <ul>
 *   <li><b>Honest signatures.</b> {@code Result<User, ValidationError>} tells the caller what can go
 *       wrong before they write a line.</li>
 *   <li><b>Composability.</b> Chaining fallible steps with {@code flatMap} short-circuits on the
 *       first failure without a single try/catch or null check.</li>
 *   <li><b>You cannot forget.</b> Unlike an unchecked exception, you cannot use the value without
 *       first deciding what to do about the error.</li>
 *   <li><b>Cost.</b> Filling in a stack trace is expensive, and for a validation failure it is
 *       entirely wasted - nobody debugs "email is missing an @" from a stack trace.</li>
 * </ul>
 *
 * <p><b>Result vs Optional.</b> {@code Optional} says "there might be nothing here". {@code Result}
 * says "there might be nothing here, <em>and this is why</em>". Use {@code Optional} for lookups
 * where absence is self-explanatory, {@code Result} when the caller needs the reason.
 *
 * <p><b>When NOT to reach for this.</b> A codebase that mixes both styles is worse than one that
 * picks either. And Java has no {@code do}-notation, so deep chains get noisy - this shines for
 * validation and parsing, not as a blanket replacement for exceptions.
 */
sealed interface Result<T, E> {

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok;
    }

    /**
     * Named {@code asOptional} rather than {@code value} on purpose: {@code Ok} is a record with a
     * {@code value()} component, and an interface method of the same name with a different return
     * type would collide. A small reminder that records reserve their component names.
     */
    default Optional<T> asOptional() {
        return this instanceof Ok<T, E> ok ? Optional.of(ok.value()) : Optional.empty();
    }

    default Optional<E> errorIfAny() {
        return this instanceof Err<T, E> err ? Optional.of(err.error()) : Optional.empty();
    }

    /** Transforms the success value. A failure passes straight through untouched. */
    default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
        if (this instanceof Ok<T, E> ok) {
            return new Ok<>(mapper.apply(ok.value()));
        }
        return new Err<>(((Err<T, E>) this).error());
    }

    /**
     * Chains another fallible step. <b>This is the method that earns the pattern its keep:</b> it is
     * what turns five nested try/catch blocks into five lines with no error handling in between,
     * short-circuiting on the first failure.
     */
    default <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> mapper) {
        if (this instanceof Ok<T, E> ok) {
            return mapper.apply(ok.value());
        }
        return new Err<>(((Err<T, E>) this).error());
    }

    /** Turns a success that fails a rule into a failure with a reason. */
    default Result<T, E> filter(Predicate<? super T> rule, E errorIfRejected) {
        if (this instanceof Ok<T, E> ok && !rule.test(ok.value())) {
            return new Err<>(errorIfRejected);
        }
        return this;
    }

    /**
     * Collapses both branches into one value - typically an HTTP response.
     *
     * <p>Forcing the caller to supply <em>both</em> handlers is the point. You cannot accidentally
     * ignore the error case, which is exactly what a bare {@code catch (Exception e) {}} lets you do.
     */
    default <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
        if (this instanceof Ok<T, E> ok) {
            return onOk.apply(ok.value());
        }
        return onErr.apply(((Err<T, E>) this).error());
    }

    default T orElse(T fallback) {
        return this instanceof Ok<T, E> ok ? ok.value() : fallback;
    }
}

record Ok<T, E>(T value) implements Result<T, E> {
}

record Err<T, E>(E error) implements Result<T, E> {
}

record User(String email, int age, String country) {
}

/** Every step returns a Result, so every step's failure mode is visible in its signature. */
class Registration {

    static int stepsExecuted;

    static Result<String, String> parseEmail(String raw) {
        stepsExecuted++;
        if (raw == null || raw.isBlank()) {
            return Result.err("email is required");
        }
        if (!raw.contains("@") || !raw.contains(".")) {
            return Result.err("'" + raw + "' is not a valid email");
        }
        return Result.ok(raw.trim().toLowerCase());
    }

    static Result<Integer, String> parseAge(String raw) {
        stepsExecuted++;
        try {
            int age = Integer.parseInt(raw.trim());
            return age >= 18 && age <= 120
                    ? Result.ok(age)
                    : Result.err("age must be between 18 and 120, got " + age);
        } catch (NumberFormatException notANumber) {
            // Catching at the boundary and converting to a Result is the right use of both tools:
            // the JDK hands us an exception, we translate it into the domain's vocabulary once.
            return Result.err("'" + raw + "' is not a number");
        }
    }

    static Result<String, String> parseCountry(String raw) {
        stepsExecuted++;
        return List.of("IN", "US", "GB").contains(raw)
                ? Result.ok(raw)
                : Result.err("'" + raw + "' is not a supported country");
    }

    /** Short-circuiting: the first failure wins and the remaining steps never run. */
    static Result<User, String> register(String email, String age, String country) {
        return parseEmail(email)
                .flatMap(validEmail -> parseAge(age)
                        .flatMap(validAge -> parseCountry(country)
                                .map(validCountry -> new User(validEmail, validAge, validCountry))));
    }

    /**
     * Accumulating: run every check and collect all the reasons.
     *
     * <p>Both modes are correct for different jobs, and knowing which to use where is the senior
     * answer. Short-circuit a pipeline where later steps depend on earlier ones (do not try to charge
     * a card if the cart failed to load). Accumulate for form validation, where telling the user
     * about one broken field at a time is a miserable experience.
     */
    static Result<User, List<String>> registerCollectingErrors(String email, String age, String country) {
        Result<String, String> emailResult = parseEmail(email);
        Result<Integer, String> ageResult = parseAge(age);
        Result<String, String> countryResult = parseCountry(country);

        List<String> errors = new ArrayList<>();
        emailResult.errorIfAny().ifPresent(errors::add);
        ageResult.errorIfAny().ifPresent(errors::add);
        countryResult.errorIfAny().ifPresent(errors::add);

        if (!errors.isEmpty()) {
            return Result.err(errors);
        }
        return Result.ok(new User(
                emailResult.asOptional().orElseThrow(),
                ageResult.asOptional().orElseThrow(),
                countryResult.asOptional().orElseThrow()));
    }
}

public class ResultDemo {

    public static void main(String[] args) {

        section("1. The happy path reads like it cannot fail");
        Registration.stepsExecuted = 0;
        System.out.println("  " + Registration.register("Priya@Example.COM ", "29", "IN")
                .fold(user -> "201 Created " + user, error -> "400 Bad Request: " + error));
        System.out.println("  steps executed: " + Registration.stepsExecuted + " of 3");

        section("2. Short-circuiting: the first failure stops the pipeline");
        Registration.stepsExecuted = 0;
        System.out.println("  " + Registration.register("not-an-email", "29", "IN")
                .fold(user -> "201 Created " + user, error -> "400 Bad Request: " + error));
        System.out.println("  steps executed: " + Registration.stepsExecuted + " of 3");
        System.out.println("  parseAge and parseCountry never ran - and there is not one try/catch");
        System.out.println("  or null check in register().");

        section("3. Every failure mode, handled uniformly");
        List<String[]> inputs = List.of(
                new String[]{"", "29", "IN"},
                new String[]{"sam@example.com", "abc", "IN"},
                new String[]{"sam@example.com", "12", "IN"},
                new String[]{"sam@example.com", "34", "FR"});
        for (String[] input : inputs) {
            System.out.printf("  %-42s -> %s%n",
                    "(\"" + input[0] + "\", \"" + input[1] + "\", \"" + input[2] + "\")",
                    Registration.register(input[0], input[1], input[2])
                            .fold(user -> "OK  " + user, error -> "ERR " + error));
        }

        section("4. Accumulating errors instead of short-circuiting");
        System.out.println("  " + Registration.registerCollectingErrors("bad", "abc", "FR")
                .fold(user -> "201 Created " + user, errors -> "422 Unprocessable:\n      - "
                        + String.join("\n      - ", errors)));
        System.out.println("  Short-circuit when later steps DEPEND on earlier ones.");
        System.out.println("  Accumulate for form validation - fixing one field at a time is miserable.");

        section("5. map, filter and orElse compose without unwrapping");
        Result<Integer, String> age = Registration.parseAge("42");
        System.out.println("  parseAge(\"42\").map(x -> x * 2)          = "
                + age.map(a -> a * 2).fold(Object::toString, e -> "ERR " + e));
        System.out.println("  .filter(under 30)                      = "
                + age.filter(a -> a < 30, "must be under 30").fold(Object::toString, e -> "ERR " + e));
        System.out.println("  parseAge(\"oops\").orElse(-1)            = "
                + Registration.parseAge("oops").orElse(-1));
        System.out.println("  The value is never unwrapped until fold(), so there is no window in which");
        System.out.println("  you can use it without having handled the error.");

        section("6. Why this beats returning null or throwing");
        System.out.println("  null            : caller cannot tell WHY, and the compiler says nothing.");
        System.out.println("  Optional        : caller knows it may be empty, still does not know why.");
        System.out.println("  unchecked throw : invisible in the signature; forgotten until production.");
        System.out.println("  checked throw   : visible, but does not compose - you cannot flatMap a throw.");
        System.out.println("  Result          : visible, composable, and impossible to ignore.");
        System.out.println();
        System.out.println("  Keep using exceptions for programmer errors and genuine infrastructure");
        System.out.println("  failures. Result is for outcomes the business already expects.");

        System.out.println("\nDone.");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}

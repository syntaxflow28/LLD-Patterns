package com.lld.problems.amazonlocker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * A pickup code, stored the way a password is stored — salted, hashed, never in the clear.
 *
 * <p><b>Why this matters in an LLD round.</b> Almost every candidate writes
 * {@code record Reservation(String otp, ...)} and moves on. The code is then in the object graph, in
 * every {@code toString()}, in every debug log, in the heap dump, and in the audit table. Anyone who
 * can read a log line can open a locker. Storing a one-way hash means a leaked database still opens
 * nothing.
 *
 * <p><b>Why {@link MessageDigest#isEqual} and not {@code Arrays.equals}.</b> {@code Arrays.equals}
 * returns as soon as two bytes differ, so response time leaks how many leading bytes were right. For
 * a six-digit keypad that is mostly theoretical, but the constant-time comparison is one method call
 * and the interviewer notices.
 *
 * <p><b>Why plain SHA-256 and not bcrypt/PBKDF2.</b> A deliberate, defensible trade-off: this secret
 * is six digits, lives for a day, and is protected by a hard attempt cap enforced at the keypad
 * ({@link Locker#recordFailedAttempt()}). Slow KDFs defend against offline brute force of a stolen
 * hash — worth adding if the retention window grows or the cap is removed. State the trade-off; do
 * not pretend it is not one.
 */
public final class AccessCode {

    private static final SecureRandom SALT_SOURCE = new SecureRandom();
    private static final int SALT_BYTES = 16;

    private final byte[] salt;
    private final byte[] hash;

    private AccessCode(byte[] salt, byte[] hash) {
        this.salt = salt;
        this.hash = hash;
    }

    /** Hashes {@code plaintext} immediately; the caller should hand its copy to the notifier and drop it. */
    public static AccessCode of(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] salt = new byte[SALT_BYTES];
        SALT_SOURCE.nextBytes(salt);
        return new AccessCode(salt, digest(salt, plaintext));
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(hash, digest(salt, candidate));
    }

    private static byte[] digest(byte[] salt, String plaintext) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(salt);
            sha256.update(plaintext.getBytes(StandardCharsets.UTF_8));
            return sha256.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Every JVM is required to ship SHA-256", ex);
        }
    }

    /** Deliberately opaque: a code must never reach a log line or a stack trace. */
    @Override
    public String toString() {
        return "AccessCode[REDACTED]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccessCode code
                && Arrays.equals(salt, code.salt)
                && MessageDigest.isEqual(hash, code.hash);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hash);
    }
}

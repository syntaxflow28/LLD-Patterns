package com.lld.problems.amazonlocker;

import java.security.SecureRandom;

/**
 * STRATEGY — how a pickup code is minted.
 *
 * <p>An axis of change with a business owner: six digits for a keypad, an alphanumeric string for a
 * scanner, a rotating QR payload for the app. Isolating it also isolates the one line of this whole
 * design that has to be cryptographically correct.
 *
 * <p><b>The bug this interface exists to prevent.</b> {@code Math.random()} and {@code new Random()}
 * are seeded from the clock and expose their internal state after a couple of outputs — an attacker
 * who collects a few of their own codes can predict everyone else's (CWE-338). Every implementation
 * below draws from {@link SecureRandom}, and {@code nextInt(bound)} is used rather than
 * {@code % bound} so the distribution stays uniform.
 */
public interface AccessCodePolicy {

    /** @return a fresh plaintext code. Callers must hash it (see {@link AccessCode}) and forget it. */
    String generate();

    /** The keypad case: fixed-length numeric PIN, leading zeros preserved. */
    final class NumericPin implements AccessCodePolicy {

        private final SecureRandom random = new SecureRandom();
        private final int digits;

        public NumericPin(int digits) {
            if (digits < 4 || digits > 12) {
                throw new IllegalArgumentException("digits must be between 4 and 12, got " + digits);
            }
            this.digits = digits;
        }

        @Override
        public String generate() {
            StringBuilder code = new StringBuilder(digits);
            for (int i = 0; i < digits; i++) {
                code.append(random.nextInt(10));
            }
            return code.toString();
        }
    }

    /**
     * The scanner case. Uses Crockford-style base 32 — no {@code I}, {@code L}, {@code O} or
     * {@code U} — so a human reading a code off a screen cannot confuse it with a digit.
     */
    final class AlphanumericCode implements AccessCodePolicy {

        private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

        private final SecureRandom random = new SecureRandom();
        private final int length;

        public AlphanumericCode(int length) {
            this.length = length;
        }

        @Override
        public String generate() {
            StringBuilder code = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            return code.toString();
        }
    }
}

package com.lld.problems.notification;

import java.util.Optional;

/**
 * TEMPLATE METHOD — every channel does the same four steps in the same order.
 *
 * <pre>
 *   1. check the recipient is reachable
 *   2. resolve the address for this channel
 *   3. render the payload in this channel's format
 *   4. hand it to the transport
 * </pre>
 *
 * <p>Steps 1 and 4 are identical for every channel; steps 2 and 3 are the only things that actually
 * differ. Without the template each channel re-implements the guard clause, and sooner or later one
 * of them forgets it and NPEs on a user with no phone number.
 *
 * <p><b>Why {@link #send} is final.</b> The order is the contract. A subclass that overrode
 * {@code send} to "just transmit quickly" would skip the reachability check and the length limit,
 * and the bug would only surface for the subset of users missing that contact field. Making the
 * skeleton final and the steps abstract is the entire point of the pattern — say "final so the
 * invariant can't be overridden" and you have explained Template Method in one sentence.
 *
 * <p><b>Why truncation lives here and not in the subclasses.</b> Every channel has a limit; only the
 * number differs. Pushing the number down to {@link Channel#maxBodyLength} and keeping the logic up
 * here is the Template Method split applied to data rather than behaviour.
 */
public abstract class AbstractChannel implements Channel {

    private final Channel.Transport transport;

    protected AbstractChannel(Channel.Transport transport) {
        this.transport = transport;
    }

    @Override
    public final DeliveryResult send(Recipient recipient, String subject, String body) {
        // Step 1 - guard. Shared by every channel, written once.
        Optional<String> address = addressFor(recipient);
        if (address.isEmpty()) {
            return DeliveryResult.unreachable(name(), recipient.id(), "no " + name().toLowerCase() + " address on file");
        }

        // Steps 2 and 3 - the parts that genuinely differ.
        String payload = truncate(render(recipient, subject, body));

        // Step 4 - shared again. Exceptions from the wire become the caller's problem to retry.
        transport.deliver(address.get(), payload);
        return DeliveryResult.ok(name(), recipient.id(), 1);
    }

    @Override
    public boolean canReach(Recipient recipient) {
        return addressFor(recipient).isPresent();
    }

    /** Which contact field this channel uses. The one-line difference between Email and Sms. */
    protected abstract Optional<String> addressFor(Recipient recipient);

    /** How the subject and body become a single payload for this medium. */
    protected abstract String render(Recipient recipient, String subject, String body);

    private String truncate(String payload) {
        int limit = maxBodyLength();
        if (payload.length() <= limit) {
            return payload;
        }
        return payload.substring(0, Math.max(0, limit - 3)) + "...";
    }

    /** Full subject line, greeting, signature. No length pressure at all. */
    public static final class Email extends AbstractChannel {

        public Email(Channel.Transport transport) {
            super(transport);
        }

        @Override
        protected Optional<String> addressFor(Recipient recipient) {
            return recipient.emailAddress();
        }

        @Override
        protected String render(Recipient recipient, String subject, String body) {
            return "Subject: " + subject + " | Hi " + recipient.name() + ", " + body;
        }

        @Override
        public String name() {
            return "EMAIL";
        }

        @Override
        public int maxBodyLength() {
            return 10_000;
        }
    }

    /**
     * 160 characters, no subject line, and every message costs real money. Those three constraints
     * are why SMS renders completely differently from email rather than sharing a formatter.
     */
    public static final class Sms extends AbstractChannel {

        public Sms(Channel.Transport transport) {
            super(transport);
        }

        @Override
        protected Optional<String> addressFor(Recipient recipient) {
            return recipient.phoneNumber();
        }

        @Override
        protected String render(Recipient recipient, String subject, String body) {
            return body; // the subject is dropped entirely - there is nowhere to put it
        }

        @Override
        public String name() {
            return "SMS";
        }

        @Override
        public int maxBodyLength() {
            return 160;
        }
    }

    /** Title plus a short body, and it silently no-ops if the app was uninstalled. */
    public static final class Push extends AbstractChannel {

        public Push(Channel.Transport transport) {
            super(transport);
        }

        @Override
        protected Optional<String> addressFor(Recipient recipient) {
            return recipient.pushToken();
        }

        @Override
        protected String render(Recipient recipient, String subject, String body) {
            return "{\"title\":\"" + subject + "\",\"body\":\"" + body + "\"}";
        }

        @Override
        public String name() {
            return "PUSH";
        }

        @Override
        public int maxBodyLength() {
            return 240;
        }
    }
}

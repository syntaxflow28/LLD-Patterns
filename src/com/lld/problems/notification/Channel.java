package com.lld.problems.notification;

/**
 * BRIDGE (the implementor side) — how a message physically leaves the building.
 *
 * <p>The whole design turns on one observation. There are M kinds of notification (alert, OTP,
 * promotion, receipt, ...) and N ways to deliver them (email, SMS, push, WhatsApp, Slack, ...).
 * If you model that with inheritance you get {@code OtpEmail}, {@code OtpSms}, {@code OtpPush},
 * {@code AlertEmail}... <b>M x N classes</b>, and adding one new channel means writing M new
 * classes. Composition instead of inheritance turns it into <b>M + N</b>: a new channel is exactly
 * one class and every existing notification type can use it immediately.
 *
 * <p><b>Say this out loud:</b> "Two dimensions vary independently, so I'll bridge them — the
 * notification holds a channel rather than inheriting from one." Interviewers are listening for the
 * words "two independent dimensions"; that is the trigger for Bridge and it is the single most
 * commonly missed pattern in this question.
 *
 * <p><b>Strategy vs Bridge</b> — the follow-up. Same object graph, different intent. Strategy swaps
 * one algorithm at runtime; Bridge exists so two whole class hierarchies can evolve separately. If
 * only the channel varied, Strategy would be the honest name. Because notification <em>types</em>
 * also form a hierarchy with their own behaviour, it is Bridge.
 */
public interface Channel {

    /**
     * The raw wire call, injected so tests do not need a real SMTP or SMS gateway.
     *
     * <p>Failure is signalled by throwing, because at this layer a dead socket genuinely is
     * exceptional — it is {@link Channel}'s job to translate that into a {@link DeliveryResult}.
     * That translation boundary is worth pointing at: exceptions below it, values above it.
     */
    @FunctionalInterface
    interface Transport {
        void deliver(String address, String payload);
    }

    DeliveryResult send(Recipient recipient, String subject, String body);

    String name();

    /** Fail fast rather than attempting - and retrying - a send that can never work. */
    boolean canReach(Recipient recipient);

    /** SMS is 160 characters; email is effectively unbounded. The channel owns its own limits. */
    int maxBodyLength();
}

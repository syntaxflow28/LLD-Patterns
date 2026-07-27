package com.lld.problems.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runnable walk-through of the notification service design.
 *
 * <pre>
 *   java -cp out com.lld.problems.notification.NotificationDemo
 * </pre>
 *
 * <p>The headline is section 1: nine type/channel combinations produced by six classes. If you take
 * one thing from this problem, it is recognising "M message types x N delivery channels" as the
 * signature of Bridge, and saying so before you start drawing boxes.
 */
public class NotificationDemo {

    public static void main(String[] args) {

        Recipient priya = Recipient.of("U-1001", "Priya", "priya@example.com", "+91-99999-11111", "dev-token-a1");
        Recipient sam = Recipient.of("U-1002", "Sam", "sam@example.com", null, null); // email only

        section("1. BRIDGE: 3 notification types x 3 channels = 9 combinations, 6 classes");
        RecordingTransport wire = new RecordingTransport();
        List<Channel> channels = List.of(
                new AbstractChannel.Email(wire),
                new AbstractChannel.Sms(wire),
                new AbstractChannel.Push(wire));

        NotificationService service = new NotificationService(RetryPolicy.noRetry(), millis -> { });
        for (Channel channel : channels) {
            for (Notification notification : messagesFor(channel)) {
                service.notify(notification, priya);
            }
        }
        wire.printAll();
        System.out.println("  Adding WhatsApp costs ONE class, not three. Adding a 'Receipt' type");
        System.out.println("  costs ONE class, not three. That is M+N instead of MxN.");

        section("2. TEMPLATE METHOD: the reachability guard is written once, not per channel");
        wire.clear();
        System.out.println("  Sam has an email address but no phone and no device token.");
        for (Channel channel : channels) {
            System.out.println("      " + service.notify(new Notification.Otp(channel, "884213", 5), sam));
        }
        System.out.println("  Note attempts=0 on the failures: unreachable is not the same as 'tried and");
        System.out.println("  failed', so the retry budget is never spent on a send that cannot work.");

        section("3. Each channel enforces its own limits");
        wire.clear();
        Channel sms = new AbstractChannel.Sms(wire);
        Channel email = new AbstractChannel.Email(wire);
        String longCampaign = "Monsoon Mega Sale on electronics, home appliances, fashion, groceries, "
                + "travel bookings and everything else you have ever wanted to buy from us this season";
        service.notify(new Notification.Promotional(sms, longCampaign, 20), priya);
        service.notify(new Notification.Promotional(email, longCampaign, 20), priya);
        wire.printAll();
        System.out.println("  Same notification object shape, two very different payloads. SMS drops the");
        System.out.println("  subject entirely and truncates at 160; email keeps the greeting.");

        section("4. Retries with exponential backoff, verified without waiting");
        FlakyTransport flaky = new FlakyTransport(2); // fails twice, then succeeds
        RecordingSleeper sleeper = new RecordingSleeper();
        NotificationService retrying = new NotificationService(
                new RetryPolicy(3, 100, 2.0, 5_000), sleeper);

        DeliveryResult recovered = retrying.notify(
                new Notification.Alert(new AbstractChannel.Push(flaky), "payments-api", "p99 latency 4.2s"), priya);
        System.out.println("      " + recovered);
        System.out.println("  backoff waits: " + sleeper.delays() + " ms  (100 then 200 - doubling)");
        System.out.println("  Fixed-interval retries from many clients synchronise into a thundering herd;");
        System.out.println("  doubling gives the failing downstream room to actually recover.");

        section("5. Retries are finite");
        FlakyTransport dead = new FlakyTransport(Integer.MAX_VALUE);
        RecordingSleeper sleeper2 = new RecordingSleeper();
        NotificationService givesUp = new NotificationService(
                new RetryPolicy(3, 100, 2.0, 5_000), sleeper2);
        System.out.println("      " + givesUp.notify(
                new Notification.Alert(new AbstractChannel.Email(dead), "search-api", "index corrupt"), priya));
        System.out.println("  backoff waits: " + sleeper2.delays() + " ms");
        System.out.println("  In production the exhausted message goes to a dead-letter queue, and a");
        System.out.println("  circuit breaker stops us retrying a provider we already know is down.");

        section("6. Opt-out applies to marketing, never to transactional");
        wire.clear();
        Recipient optedOut = priya.mute("promotions");
        NotificationService plain = new NotificationService(RetryPolicy.noRetry(), millis -> { });
        Channel push = new AbstractChannel.Push(wire);

        System.out.println("      " + plain.notify(new Notification.Promotional(push, "Monsoon Sale", 20), optedOut));
        System.out.println("      " + plain.notify(new Notification.Otp(push, "884213", 5), optedOut));
        System.out.println("      " + plain.notify(new Notification.Alert(push, "billing", "card expired"), optedOut));
        System.out.println("  The promotion is suppressed; the OTP and the alert are not. Suppressing an");
        System.out.println("  OTP would lock the user out of their own account - that is why the flag lives");
        System.out.println("  on the notification type and not on the channel.");

        section("7. Adding a fourth channel: one class, works with every existing type");
        wire.clear();
        Channel slack = new SlackChannel(wire);
        for (Notification notification : messagesFor(slack)) {
            plain.notify(notification, priya);
        }
        wire.printAll();
        System.out.println("  Zero edits to Notification, Alert, Otp, Promotional or NotificationService.");
        System.out.println("  That is the Open/Closed principle paying for the Bridge indirection.");

        section("8. OBSERVER: auditing without touching the service");
        List<String> auditTrail = new ArrayList<>();
        NotificationService audited = new NotificationService(RetryPolicy.noRetry(), millis -> { });
        audited.addAuditor(result -> auditTrail.add(result.channel() + ":" + (result.delivered() ? "ok" : "fail")));
        audited.notify(new Notification.Otp(new AbstractChannel.Email(wire), "111222", 5), priya);
        audited.notify(new Notification.Otp(new AbstractChannel.Sms(wire), "111222", 5), sam);
        System.out.println("  audit trail: " + auditTrail);

        System.out.println("\nDone.");
    }

    private static List<Notification> messagesFor(Channel channel) {
        return List.of(
                new Notification.Alert(channel, "payments-api", "p99 latency 4.2s"),
                new Notification.Otp(channel, "884213", 5),
                new Notification.Promotional(channel, "Monsoon Sale", 20));
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /**
     * The fourth channel, defined here in the demo to make the "one class" claim literal. It reaches
     * everyone because a Slack workspace id is always present - a useful contrast with SMS.
     */
    static final class SlackChannel extends AbstractChannel {

        SlackChannel(Channel.Transport transport) {
            super(transport);
        }

        @Override
        protected Optional<String> addressFor(Recipient recipient) {
            return Optional.of("@" + recipient.id());
        }

        @Override
        protected String render(Recipient recipient, String subject, String body) {
            return "*" + subject + "*\n" + body;
        }

        @Override
        public String name() {
            return "SLACK";
        }

        @Override
        public int maxBodyLength() {
            return 4_000;
        }
    }

    /** Captures what would have gone on the wire, so the demo can print it instead of sending it. */
    static final class RecordingTransport implements Channel.Transport {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void deliver(String address, String payload) {
            sent.add(String.format("%-22s %s", address, payload.replace("\n", " \\n ")));
        }

        void printAll() {
            sent.forEach(line -> System.out.println("      " + line));
        }

        void clear() {
            sent.clear();
        }
    }

    /** Fails the first {@code failures} calls, then succeeds. Simulates a recovering provider. */
    static final class FlakyTransport implements Channel.Transport {

        private final int failures;
        private int calls;

        FlakyTransport(int failures) {
            this.failures = failures;
        }

        @Override
        public void deliver(String address, String payload) {
            calls++;
            if (calls <= failures) {
                throw new IllegalStateException("gateway timeout (attempt " + calls + ")");
            }
        }
    }

    /** Records what the backoff WOULD have waited, without waiting. */
    static final class RecordingSleeper implements NotificationService.Sleeper {

        private final List<Long> delays = new ArrayList<>();

        @Override
        public void sleepMillis(long millis) {
            delays.add(millis);
        }

        List<Long> delays() {
            return List.copyOf(delays);
        }
    }
}

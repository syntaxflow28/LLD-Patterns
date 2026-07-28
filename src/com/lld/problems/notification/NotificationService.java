package com.lld.problems.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * FACADE — the one entry point the rest of the application calls.
 *
 * <p>Three concerns are layered here, and keeping them in this order is the design:
 * <ol>
 *   <li><b>Should we send at all?</b> Preference and opt-out checks. Cheapest, so it goes first —
 *       and it is a compliance requirement, not an optimisation.</li>
 *   <li><b>Can we send?</b> Reachability. Also free, and it turns a guaranteed-to-fail send into a
 *       clear result instead of three pointless retries against a null address.</li>
 *   <li><b>Send, with retries.</b> Only now do we touch the network.</li>
 * </ol>
 *
 * <p><b>Why {@link Sleeper} is injected.</b> Same reason {@code Clock} and {@code TimeSource} are
 * injected elsewhere in this repo: a retry test that actually sleeps for 100ms + 200ms is a slow,
 * flaky test. With a recording no-op sleeper the demo can <em>prove</em> the backoff sequence
 * instead of asserting it took roughly the right wall-clock time.
 *
 * <p><b>What this would look like at scale, if asked.</b> {@code notify} would enqueue to Kafka and
 * return immediately, with workers consuming per channel so a slow SMS provider cannot back up
 * email. Add an idempotency key so a redelivered queue message does not send the OTP twice. The
 * synchronous version here is the right thing to draw first, then evolve.
 */
public class NotificationService {

    /** Injected so backoff can be verified without real waiting. */
    @FunctionalInterface
    public interface Sleeper {
        void sleepMillis(long millis);

        static Sleeper real() {
            return millis -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // never swallow the interrupt flag
                    throw new IllegalStateException("interrupted while backing off", e);
                }
            };
        }
    }

    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final List<Consumer<DeliveryResult>> auditors = new CopyOnWriteArrayList<>();

    public NotificationService(RetryPolicy retryPolicy, Sleeper sleeper) {
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    /** OBSERVER: metrics, audit trails and dashboards attach here without the service knowing. */
    public void addAuditor(Consumer<DeliveryResult> auditor) {
        auditors.add(auditor);
    }

    public DeliveryResult notify(Notification notification, Recipient recipient) {
        DeliveryResult result = deliver(notification, recipient);
        auditors.forEach(auditor -> auditor.accept(result));
        return result;
    }

    /**
     * Bulk send. One bad recipient must never abort the batch — which is exactly why
     * {@link DeliveryResult} is a return value rather than an exception.
     */
    public List<DeliveryResult> notifyAll(Notification notification, List<Recipient> recipients) {
        List<DeliveryResult> results = new ArrayList<>(recipients.size());
        for (Recipient recipient : recipients) {
            results.add(notify(notification, recipient));
        }
        return results;
    }

    private DeliveryResult deliver(Notification notification, Recipient recipient) {
        Channel channel = notification.channel();

        // 1. Preferences. Transactional messages are exempt: suppressing an OTP is a bug, not a
        //    courtesy. Getting this exemption right is what interviewers probe on this feature.
        if (!notification.isTransactional() && recipient.hasMuted(notification.category())) {
            return DeliveryResult.unreachable(channel.name(), recipient.id(),
                    "suppressed: recipient muted category '" + notification.category() + "'");
        }

        // 2. Reachability. Retrying a send to an address that does not exist wastes the whole
        //    backoff budget on a request that can never succeed.
        if (!channel.canReach(recipient)) {
            return DeliveryResult.unreachable(channel.name(), recipient.id(),
                    "no address for channel " + channel.name());
        }

        // 3. Attempt, back off, repeat.
        int attempts = 0;
        RuntimeException lastFailure = null;
        while (retryPolicy.shouldRetry(attempts)) {
            attempts++;
            try {
                notification.deliverTo(recipient);
                return DeliveryResult.ok(channel.name(), recipient.id(), attempts);
            } catch (RuntimeException transportFailure) {
                lastFailure = transportFailure;
                if (retryPolicy.shouldRetry(attempts)) {
                    sleeper.sleepMillis(retryPolicy.backoffMillis(attempts));
                }
            }
        }
        return DeliveryResult.failed(channel.name(), recipient.id(), attempts,
                "gave up after " + attempts + " attempts: "
                        + (lastFailure == null ? "unknown" : lastFailure.getMessage()));
    }
}

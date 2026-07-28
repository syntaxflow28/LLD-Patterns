package com.lld.problems.notification;

/**
 * BRIDGE (the abstraction side) — what we are saying, independent of how it travels.
 *
 * <p>Each subclass owns its <em>content and policy</em>: the subject and body it renders, the
 * category it belongs to for opt-out purposes, and whether it is transactional (must always be
 * delivered) or marketing (must respect preferences). None of them know what a channel is beyond
 * holding one.
 *
 * <p><b>The M x N escape, concretely.</b> Three notification types and three channels here. The
 * inheritance version needs nine classes and grows by three every time a channel is added. This
 * version needs six and grows by one. Run {@code NotificationDemo} section 1 to see all nine
 * combinations produced by six classes.
 *
 * <p><b>Why {@code isTransactional} lives on the notification and not the channel.</b> Whether a
 * message may be suppressed is a property of the message: an OTP must go out even to a user who has
 * muted everything, a discount code must not. Putting that flag on the channel would mean "SMS is
 * transactional", which is nonsense — you send both kinds over SMS.
 */
public abstract class Notification {

    private final Channel channel; // the bridge: composition, not inheritance

    protected Notification(Channel channel) {
        this.channel = channel;
    }

    public abstract String subject();

    public abstract String body();

    /** Used for per-category opt-out ("mute promotions but keep order updates"). */
    public abstract String category();

    /** Transactional messages bypass preference checks; marketing messages do not. */
    public boolean isTransactional() {
        return true;
    }

    /**
     * The bridge crossing. Everything above this line is content; everything below is transport,
     * and neither side has to change when the other does.
     */
    public DeliveryResult deliverTo(Recipient recipient) {
        return channel.send(recipient, subject(), body());
    }

    public Channel channel() {
        return channel;
    }

    /** Something broke. Transactional, and deliberately terse so it survives SMS truncation. */
    public static final class Alert extends Notification {

        private final String service;
        private final String detail;

        public Alert(Channel channel, String service, String detail) {
            super(channel);
            this.service = service;
            this.detail = detail;
        }

        @Override
        public String subject() {
            return "[ALERT] " + service;
        }

        @Override
        public String body() {
            return service + " is unhealthy: " + detail;
        }

        @Override
        public String category() {
            return "alerts";
        }
    }

    /**
     * A one-time passcode. The most transactional message there is: if this is suppressed the user
     * literally cannot log in, which is why {@link #isTransactional} stays true.
     */
    public static final class Otp extends Notification {

        private final String code;
        private final int validForMinutes;

        public Otp(Channel channel, String code, int validForMinutes) {
            super(channel);
            this.code = code;
            this.validForMinutes = validForMinutes;
        }

        @Override
        public String subject() {
            return "Your verification code";
        }

        @Override
        public String body() {
            return code + " is your code. Valid for " + validForMinutes + " minutes. Do not share it.";
        }

        @Override
        public String category() {
            return "security";
        }
    }

    /** Marketing. The one type that must be suppressible, and the reason the flag exists at all. */
    public static final class Promotional extends Notification {

        private final String campaign;
        private final int discountPercent;

        public Promotional(Channel channel, String campaign, int discountPercent) {
            super(channel);
            this.campaign = campaign;
            this.discountPercent = discountPercent;
        }

        @Override
        public String subject() {
            return campaign;
        }

        @Override
        public String body() {
            return "Enjoy " + discountPercent + "% off this week. " + campaign + ". Reply STOP to opt out.";
        }

        @Override
        public String category() {
            return "promotions";
        }

        @Override
        public boolean isTransactional() {
            return false;
        }
    }
}

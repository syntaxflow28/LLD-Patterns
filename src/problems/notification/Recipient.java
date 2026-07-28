package problems.notification;

import java.util.Optional;
import java.util.Set;

/**
 * Who we are sending to, and how they can be reached.
 *
 * <p><b>Why the contact fields are nullable and read through {@link Optional}.</b> Real users are
 * incomplete: signed up with a phone number and never added an email, or uninstalled the app so the
 * push token is dead. Modelling that honestly is what lets {@link Channel#canReach} exist and
 * prevents the single most common bug in this design — blindly sending an SMS to {@code null} and
 * discovering it only from a support ticket.
 *
 * <p><b>Why preferences live on the recipient.</b> Marketing opt-out is a property of the person,
 * not of the message or the transport. Putting it here means the check happens once, in
 * {@link NotificationService}, instead of being re-implemented inside every channel.
 *
 * <p>In production this record is also where GDPR/CAN-SPAM consent and quiet hours would sit — the
 * "can we legally send this right now" question is a genuine design axis interviewers like to add
 * halfway through.
 */
public record Recipient(
        String id,
        String name,
        String email,
        String phone,
        String deviceToken,
        Set<String> mutedCategories) {

    public Recipient {
        mutedCategories = Set.copyOf(mutedCategories); // defensive: records are only shallowly immutable
    }

    public static Recipient of(String id, String name, String email, String phone, String deviceToken) {
        return new Recipient(id, name, email, phone, deviceToken, Set.of());
    }

    public Optional<String> emailAddress() {
        return Optional.ofNullable(email);
    }

    public Optional<String> phoneNumber() {
        return Optional.ofNullable(phone);
    }

    public Optional<String> pushToken() {
        return Optional.ofNullable(deviceToken);
    }

    public boolean hasMuted(String category) {
        return mutedCategories.contains(category);
    }

    public Recipient mute(String category) {
        Set<String> updated = new java.util.HashSet<>(mutedCategories);
        updated.add(category);
        return new Recipient(id, name, email, phone, deviceToken, updated);
    }
}

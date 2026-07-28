package problems.parkinglot;

import java.time.Instant;

/**
 * The ticket is the receipt for a parking session. It is immutable: the exit time and fee are
 * <em>not</em> fields on it, because a ticket that mutates is a ticket you cannot audit.
 *
 * <p>Interviewers sometimes push for a {@code TicketStatus} enum (ISSUED / PAID / LOST). That is a
 * fair extension, but only add it once you have a payment step — a status field with two values and
 * no transitions is noise.
 */
public record Ticket(String id, Vehicle vehicle, ParkingSpot spot, Instant entryTime) {
}

package problems.parkinglot;

import java.math.BigDecimal;

/**
 * OBSERVER — the lot publishes what happened; it does not know or care who is listening.
 *
 * <p>The requirement "the display board shows free spots per floor" is a trap. The obvious answer
 * is to give {@code ParkingLot} a {@code DisplayBoard} field and call it directly. Then finance
 * wants an audit log, ops want metrics, and marketing wants an app notification — and the lot ends
 * up depending on four unrelated subsystems.
 *
 * <p>{@code default} methods mean a listener implements only the events it cares about.
 */
public interface ParkingLotListener {

    default void onVehicleEntered(Ticket ticket) {
    }

    default void onVehicleExited(Ticket ticket, BigDecimal fee) {
    }

    /** The display board at the entrance. Pull-based: it asks the lot for current counts. */
    final class DisplayBoard implements ParkingLotListener {

        private final ParkingLot lot;

        public DisplayBoard(ParkingLot lot) {
            this.lot = lot;
        }

        @Override
        public void onVehicleEntered(Ticket ticket) {
            print();
        }

        @Override
        public void onVehicleExited(Ticket ticket, BigDecimal fee) {
            print();
        }

        private void print() {
            StringBuilder sb = new StringBuilder("     BOARD |");
            for (SpotType type : SpotType.values()) {
                sb.append(' ').append(type).append('=').append(lot.availability().get(type)).append(" |");
            }
            System.out.println(sb);
        }
    }

    /** An audit trail. Added without touching ParkingLot — that is the whole point. */
    final class AuditLog implements ParkingLotListener {

        @Override
        public void onVehicleEntered(Ticket ticket) {
            System.out.println("     AUDIT | IN  " + ticket.vehicle().licensePlate()
                    + " -> " + ticket.spot() + " ticket=" + ticket.id());
        }

        @Override
        public void onVehicleExited(Ticket ticket, BigDecimal fee) {
            System.out.println("     AUDIT | OUT " + ticket.vehicle().licensePlate()
                    + " <- " + ticket.spot() + " fee=" + fee);
        }
    }
}

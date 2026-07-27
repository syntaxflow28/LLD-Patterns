package com.lld.problems.meetingscheduler;

/**
 * A bookable room.
 *
 * @param id       display name, used as the key
 * @param capacity how many people fit
 * @param floor    which floor it is on, so "any room" can prefer nearby ones
 */
public record Room(String id, int capacity, int floor) {

    public Room {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
    }

    public boolean fits(int attendees) {
        return capacity >= attendees;
    }

    @Override
    public String toString() {
        return id + " (seats " + capacity + ", floor " + floor + ")";
    }
}

package me.nawa.appointment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipStatusTest {

    @Test
    void canTransitionTo_depositHeld_allowsActive() {
        assertTrue(MembershipStatus.PENDING.canTransitionTo(
                MembershipStatus.ACTIVE
        ));
    }

    @Test
    void canTransitionTo_leave_allowsLeft() {
        assertTrue(MembershipStatus.PENDING.canTransitionTo(
                MembershipStatus.LEFT
        ));
        assertTrue(MembershipStatus.ACTIVE.canTransitionTo(
                MembershipStatus.LEFT
        ));
    }

    @Test
    void canTransitionTo_left_rejectsRejoin() {
        assertFalse(MembershipStatus.LEFT.canTransitionTo(
                MembershipStatus.PENDING
        ));
        assertFalse(MembershipStatus.LEFT.canTransitionTo(
                MembershipStatus.ACTIVE
        ));
    }
}

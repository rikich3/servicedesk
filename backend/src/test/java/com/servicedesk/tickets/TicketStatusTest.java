package com.servicedesk.tickets;

import static com.servicedesk.tickets.TicketStatus.CLOSED;
import static com.servicedesk.tickets.TicketStatus.IN_PROGRESS;
import static com.servicedesk.tickets.TicketStatus.OPEN;
import static com.servicedesk.tickets.TicketStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketStatusTest {

    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of(OPEN, IN_PROGRESS),
                Arguments.of(IN_PROGRESS, RESOLVED),
                Arguments.of(IN_PROGRESS, OPEN),
                Arguments.of(RESOLVED, CLOSED),
                Arguments.of(RESOLVED, OPEN));
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.of(OPEN, RESOLVED),
                Arguments.of(OPEN, CLOSED),
                Arguments.of(OPEN, OPEN),
                Arguments.of(IN_PROGRESS, CLOSED),
                Arguments.of(RESOLVED, IN_PROGRESS),
                Arguments.of(CLOSED, OPEN),
                Arguments.of(CLOSED, RESOLVED),
                Arguments.of(CLOSED, IN_PROGRESS));
    }

    @ParameterizedTest
    @MethodSource("allowed")
    void allowsDefinedTransitions(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("rejected")
    void rejectsEverythingElse(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void closedIsTerminal() {
        assertThat(CLOSED.allowedTargets()).isEmpty();
    }
}

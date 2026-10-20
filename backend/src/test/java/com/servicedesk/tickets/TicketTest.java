package com.servicedesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.common.BusinessRuleException;
import com.servicedesk.identity.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketTest {

    private final User requester = new User("req@test.local", "x", "Req");
    private final User tech = new User("tech@test.local", "x", "Tech");
    private final Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket(1L, "Title", "Desc", TicketPriority.MEDIUM, requester);
    }

    @Test
    void newTicketIsOpenAndUnassigned() {
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void cannotStartWithoutAssignee() {
        assertThatThrownBy(() -> ticket.start(t0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Assign a technician");
    }

    @Test
    void happyPathRecordsTimestamps() {
        ticket.assignTo(tech);
        ticket.start(t0);
        ticket.resolve(t0.plusSeconds(3600));
        ticket.close(t0.plusSeconds(7200));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getResolvedAt()).isEqualTo(t0.plusSeconds(3600));
        assertThat(ticket.getClosedAt()).isEqualTo(t0.plusSeconds(7200));
    }

    @Test
    void reopenClearsResolutionAndCountsIt() {
        ticket.assignTo(tech);
        ticket.start(t0);
        ticket.resolve(t0);
        ticket.transitionTo(TicketStatus.OPEN, t0);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getReopenCount()).isEqualTo(1);
        assertThat(ticket.getAssignee()).as("assignee keeps context on reopen").isEqualTo(tech);
    }

    @Test
    void returningToQueueDropsAssignee() {
        ticket.assignTo(tech);
        ticket.start(t0);
        ticket.transitionTo(TicketStatus.OPEN, t0);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getReopenCount()).isZero();
    }

    @Test
    void resolvingAnOpenTicketIsRejected() {
        assertThatThrownBy(() -> ticket.resolve(t0))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("OPEN to RESOLVED");
    }

    @Test
    void closedTicketCannotBeReopened() {
        ticket.assignTo(tech);
        ticket.start(t0);
        ticket.resolve(t0);
        ticket.close(t0);
        assertThatThrownBy(() -> ticket.reopen(t0)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void cannotAssignResolvedTicket() {
        ticket.assignTo(tech);
        ticket.start(t0);
        ticket.resolve(t0);
        assertThatThrownBy(() -> ticket.assignTo(requester)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotUnassignWhileInProgress() {
        ticket.assignTo(tech);
        ticket.start(t0);
        assertThatThrownBy(ticket::unassign).isInstanceOf(BusinessRuleException.class);
    }
}

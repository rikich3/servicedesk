package com.servicedesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Organization;
import com.servicedesk.support.IntegrationTest;
import com.servicedesk.support.TestData;
import com.servicedesk.tickets.TicketDtos.UpdateTicketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The ticket update and its history entry are one unit of work: if writing the event fails,
 * the ticket change is rolled back too. No partial updates survive.
 */
@IntegrationTest
class TransactionAtomicityIT {

    @Autowired TicketService service;
    @Autowired TicketRepository tickets;
    @Autowired TestData data;
    @MockitoSpyBean TicketEventRepository events;

    Organization org;
    User admin;
    Ticket ticket;

    @BeforeEach
    void seed() {
        data.reset();
        org = data.org("Acme");
        admin = data.user("admin");
        data.member(admin, org, MemberRole.ADMIN);
        ticket = data.ticket(org, admin, "Atomic");
    }

    @Test
    void eventWriteFailureRollsBackTheTicketChange() {
        doThrow(new RuntimeException("audit store unavailable")).when(events).save(any(TicketEvent.class));

        assertThatThrownBy(() -> service.update(org.getId(), admin.getId(), ticket.getId(),
                new UpdateTicketRequest(0L, null, TicketPriority.URGENT, null, null)))
                .hasMessageContaining("audit store unavailable");

        Ticket reloaded = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(events.findAllByTicketId(ticket.getId())).isEmpty();
    }
}

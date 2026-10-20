package com.servicedesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Membership;
import com.servicedesk.organizations.Organization;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TicketPolicyTest {

    private final TicketPolicy policy = new TicketPolicy();
    private final Organization org = new Organization("Org", "org");
    private final User requester = withId(new User("r@t", "x", "R"), 1L);
    private final User otherRequester = withId(new User("o@t", "x", "O"), 2L);
    private final User tech = withId(new User("t@t", "x", "T"), 3L);
    private final User otherTech = withId(new User("t2@t", "x", "T2"), 4L);
    private final User admin = withId(new User("a@t", "x", "A"), 5L);

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Membership as(User user, MemberRole role) {
        return new Membership(user, org, role);
    }

    private Ticket openTicket() {
        return new Ticket(1L, "t", "d", TicketPriority.LOW, requester);
    }

    @Test
    void requesterOnlySeesOwnTickets() {
        Ticket ticket = openTicket();
        assertThat(policy.canView(as(requester, MemberRole.REQUESTER), ticket)).isTrue();
        assertThat(policy.canView(as(otherRequester, MemberRole.REQUESTER), ticket)).isFalse();
        assertThat(policy.canView(as(tech, MemberRole.TECHNICIAN), ticket)).isTrue();
    }

    @Test
    void technicianCanClaimOnlyUnassignedTickets() {
        Ticket ticket = openTicket();
        assertThat(policy.canClaim(as(tech, MemberRole.TECHNICIAN), ticket)).isTrue();
        ticket.assignTo(otherTech);
        assertThat(policy.canClaim(as(tech, MemberRole.TECHNICIAN), ticket)).isFalse();
        assertThat(policy.canClaim(as(requester, MemberRole.REQUESTER), openTicket())).isFalse();
    }

    @Test
    void assignedTechnicianDrivesTheTicket() {
        Ticket ticket = openTicket();
        ticket.assignTo(tech);
        assertThat(policy.allowedTransitions(as(tech, MemberRole.TECHNICIAN), ticket))
                .containsExactly(TicketStatus.IN_PROGRESS);
        assertThat(policy.allowedTransitions(as(otherTech, MemberRole.TECHNICIAN), ticket)).isEmpty();

        ticket.start(Instant.now());
        assertThat(policy.allowedTransitions(as(tech, MemberRole.TECHNICIAN), ticket))
                .containsExactlyInAnyOrder(TicketStatus.RESOLVED, TicketStatus.OPEN);

        // Once resolved, the technician is done: reopening or closing belongs to the requester/admin.
        ticket.resolve(Instant.now());
        assertThat(policy.allowedTransitions(as(tech, MemberRole.TECHNICIAN), ticket)).isEmpty();
        assertThat(policy.allowedTransitions(as(admin, MemberRole.ADMIN), ticket))
                .containsExactlyInAnyOrder(TicketStatus.CLOSED, TicketStatus.OPEN);
    }

    @Test
    void requesterCanCloseOrReopenOnlyResolvedOwnTickets() {
        Ticket ticket = openTicket();
        assertThat(policy.allowedTransitions(as(requester, MemberRole.REQUESTER), ticket)).isEmpty();
        ticket.assignTo(tech);
        ticket.start(Instant.now());
        ticket.resolve(Instant.now());
        assertThat(policy.allowedTransitions(as(requester, MemberRole.REQUESTER), ticket))
                .containsExactlyInAnyOrder(TicketStatus.CLOSED, TicketStatus.OPEN);
        assertThat(policy.allowedTransitions(as(otherRequester, MemberRole.REQUESTER), ticket)).isEmpty();
    }

    @Test
    void adminIsBoundOnlyByTheStateMachine() {
        Ticket ticket = openTicket();
        assertThat(policy.allowedTransitions(as(admin, MemberRole.ADMIN), ticket))
                .containsExactly(TicketStatus.IN_PROGRESS);
        assertThat(policy.canAssign(as(admin, MemberRole.ADMIN))).isTrue();
        assertThat(policy.canAssign(as(tech, MemberRole.TECHNICIAN))).isFalse();
    }
}

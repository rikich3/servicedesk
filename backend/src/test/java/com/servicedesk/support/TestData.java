package com.servicedesk.support;

import com.servicedesk.identity.User;
import com.servicedesk.identity.UserRepository;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Membership;
import com.servicedesk.organizations.MembershipRepository;
import com.servicedesk.organizations.Organization;
import com.servicedesk.organizations.OrganizationRepository;
import com.servicedesk.tickets.Ticket;
import com.servicedesk.tickets.TicketPriority;
import com.servicedesk.tickets.TicketRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Builders for integration tests. Each call produces unique emails/slugs so tests stay independent. */
@Component
public class TestData {

    public static final String PASSWORD = "password123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UserRepository users;
    private final OrganizationRepository orgs;
    private final MembershipRepository memberships;
    private final TicketRepository tickets;
    private final PasswordEncoder encoder;
    private final JdbcClient jdbc;

    public TestData(UserRepository users, OrganizationRepository orgs, MembershipRepository memberships,
                    TicketRepository tickets, PasswordEncoder encoder, JdbcClient jdbc) {
        this.users = users;
        this.orgs = orgs;
        this.memberships = memberships;
        this.tickets = tickets;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    public User user(String label) {
        int n = SEQ.incrementAndGet();
        return users.save(new User(label + n + "@test.local", encoder.encode(PASSWORD), label + " " + n));
    }

    public Organization org(String name) {
        int n = SEQ.incrementAndGet();
        return orgs.save(new Organization(name + " " + n, name.toLowerCase() + "-" + n));
    }

    public Membership member(User user, Organization org, MemberRole role) {
        return memberships.save(new Membership(user, org, role));
    }

    public Ticket ticket(Organization org, User requester, String title) {
        return tickets.save(new Ticket(org.getId(), title, "Description of " + title, TicketPriority.MEDIUM, requester));
    }

    /** Wipes every table between tests; cheaper than restarting the embedded database. */
    public void reset() {
        jdbc.sql("TRUNCATE TABLE ticket_events, comments, tickets, memberships, organizations, users RESTART IDENTITY CASCADE")
                .update();
    }
}

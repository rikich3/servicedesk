package com.servicedesk.config;

import com.servicedesk.identity.User;
import com.servicedesk.identity.UserRepository;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Membership;
import com.servicedesk.organizations.MembershipRepository;
import com.servicedesk.organizations.Organization;
import com.servicedesk.organizations.OrganizationRepository;
import com.servicedesk.tickets.Comment;
import com.servicedesk.tickets.CommentRepository;
import com.servicedesk.tickets.Ticket;
import com.servicedesk.tickets.TicketEvent;
import com.servicedesk.tickets.TicketEventRepository;
import com.servicedesk.tickets.TicketEventType;
import com.servicedesk.tickets.TicketPriority;
import com.servicedesk.tickets.TicketRepository;
import com.servicedesk.tickets.TicketStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds two organizations with realistic tickets for demos and manual testing.
 * Enabled with {@code servicedesk.seed-demo-data=true}; idempotent (skips when users exist).
 * Every demo account uses the password {@code password}.
 */
@Configuration
@ConditionalOnProperty(name = "servicedesk.seed-demo-data", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String PASSWORD = "password";

    @Bean
    ApplicationRunner seedDemoData(UserRepository users, OrganizationRepository orgs, MembershipRepository memberships,
                                   TicketRepository tickets, CommentRepository comments, TicketEventRepository events,
                                   PasswordEncoder encoder, TransactionTemplate tx, JdbcClient jdbc) {
        return args -> tx.executeWithoutResult(status -> {
            if (users.count() > 0) {
                log.info("Demo data already present, skipping seed");
                return;
            }
            String hash = encoder.encode(PASSWORD);
            User admin = users.save(new User("admin@acme.test", hash, "Alice Admin"));
            User tech1 = users.save(new User("tech@acme.test", hash, "Tom Technician"));
            User tech2 = users.save(new User("tech2@acme.test", hash, "Tina Technician"));
            User req1 = users.save(new User("user@acme.test", hash, "Rita Requester"));
            User req2 = users.save(new User("user2@acme.test", hash, "Raúl Requester"));
            User outsider = users.save(new User("admin@globex.test", hash, "Gloria Globex"));

            Organization acme = orgs.save(new Organization("Acme Corp", "acme"));
            Organization globex = orgs.save(new Organization("Globex Inc", "globex"));
            memberships.save(new Membership(admin, acme, MemberRole.ADMIN));
            memberships.save(new Membership(tech1, acme, MemberRole.TECHNICIAN));
            memberships.save(new Membership(tech2, acme, MemberRole.TECHNICIAN));
            memberships.save(new Membership(req1, acme, MemberRole.REQUESTER));
            memberships.save(new Membership(req2, acme, MemberRole.REQUESTER));
            memberships.save(new Membership(outsider, globex, MemberRole.ADMIN));
            memberships.save(new Membership(tech1, globex, MemberRole.TECHNICIAN)); // same person, different role elsewhere

            Instant now = Instant.now();
            Object[][] rows = {
                // title, description, priority, requester, assignee, status, daysAgo, resolveAfterHours
                {"Cannot sign in to email", "Password rejected since this morning, no reset email arrives.", TicketPriority.HIGH, req1, tech1, TicketStatus.IN_PROGRESS, 2, null},
                {"Printer on 3rd floor jams", "Every second page jams in the tray.", TicketPriority.LOW, req2, null, TicketStatus.OPEN, 1, null},
                {"VPN drops every 10 minutes", "Happens on home Wi-Fi only.", TicketPriority.URGENT, req1, tech2, TicketStatus.IN_PROGRESS, 3, null},
                {"Request second monitor", "For the finance team desk 12.", TicketPriority.MEDIUM, req2, tech1, TicketStatus.RESOLVED, 7, 20},
                {"Laptop fan very loud", "Started after the last update.", TicketPriority.MEDIUM, req1, tech2, TicketStatus.CLOSED, 12, 30},
                {"Shared drive permission missing", "Need read access to /Sales/2026.", TicketPriority.HIGH, req2, tech1, TicketStatus.CLOSED, 20, 5},
                {"Keyboard keys sticking", "Spacebar and enter.", TicketPriority.LOW, req1, tech2, TicketStatus.RESOLVED, 10, 48},
                {"New hire onboarding accounts", "Three starters on Monday.", TicketPriority.HIGH, req2, null, TicketStatus.OPEN, 0, null},
                {"Slow Wi-Fi in meeting room B", "Video calls freeze.", TicketPriority.MEDIUM, req1, tech1, TicketStatus.CLOSED, 25, 70},
                {"Install design software", "Figma desktop and Illustrator.", TicketPriority.LOW, req2, tech2, TicketStatus.RESOLVED, 4, 10},
            };
            for (Object[] r : rows) {
                Ticket t = new Ticket(acme.getId(), (String) r[0], (String) r[1], (TicketPriority) r[2], (User) r[3]);
                User assignee = (User) r[4];
                TicketStatus target = (TicketStatus) r[5];
                Instant created = now.minus((int) r[6], ChronoUnit.DAYS);
                t = tickets.save(t);
                events.save(new TicketEvent(t, (User) r[3], TicketEventType.CREATED, null, "OPEN"));
                if (assignee != null) {
                    t.assignTo(assignee);
                    events.save(new TicketEvent(t, admin, TicketEventType.ASSIGNED, null, assignee.getFullName()));
                }
                if (target != TicketStatus.OPEN) {
                    t.start(created);
                    events.save(new TicketEvent(t, assignee, TicketEventType.STATUS_CHANGED, "OPEN", "IN_PROGRESS"));
                    comments.save(new Comment(t, assignee, "Looking into it now."));
                    events.save(new TicketEvent(t, assignee, TicketEventType.COMMENTED, null, null));
                }
                if (target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED) {
                    Instant resolved = created.plus((int) r[7], ChronoUnit.HOURS);
                    t.resolve(resolved);
                    comments.save(new Comment(t, assignee, "Fixed. Let me know if it comes back."));
                    events.save(new TicketEvent(t, assignee, TicketEventType.STATUS_CHANGED, "IN_PROGRESS", "RESOLVED"));
                }
                if (target == TicketStatus.CLOSED) {
                    t.close(created.plus((int) r[7] + 24, ChronoUnit.HOURS));
                    events.save(new TicketEvent(t, (User) r[3], TicketEventType.STATUS_CHANGED, "RESOLVED", "CLOSED"));
                }
                tickets.flush();
                // Backdate timestamps directly: entities stamp "now" on insert.
                jdbc.sql("UPDATE tickets SET created_at = :c, updated_at = :c WHERE id = :id")
                        .param("c", java.sql.Timestamp.from(created)).param("id", t.getId()).update();
            }
            Ticket globexTicket = tickets.save(new Ticket(globex.getId(), "Globex-only ticket",
                    "Visible only to Globex members.", TicketPriority.MEDIUM, outsider));
            events.save(new TicketEvent(globexTicket, outsider, TicketEventType.CREATED, null, "OPEN"));
            log.info("Seeded demo data: admin@acme.test / tech@acme.test / user@acme.test (password: {})", PASSWORD);
        });
    }
}

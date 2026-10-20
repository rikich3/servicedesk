package com.servicedesk.reports;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Organization;
import com.servicedesk.support.ApiIntegrationTest;
import com.servicedesk.support.TestData;
import com.servicedesk.tickets.Ticket;
import com.servicedesk.tickets.TicketRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;

class ReportsIT extends ApiIntegrationTest {

    @Autowired TicketRepository tickets;
    @Autowired JdbcClient jdbc;

    @Test
    void summaryAggregatesStatusWorkloadAndResolutionTime() throws Exception {
        Organization org = data.org("Acme");
        Organization other = data.org("Globex");
        User admin = data.user("admin");
        User tech = data.user("tech");
        User tech2 = data.user("tech2");
        User requester = data.user("req");
        data.member(admin, org, MemberRole.ADMIN);
        data.member(tech, org, MemberRole.TECHNICIAN);
        data.member(tech2, org, MemberRole.TECHNICIAN);
        data.member(requester, org, MemberRole.REQUESTER);
        data.member(tech, other, MemberRole.TECHNICIAN);

        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        // tech: one in progress, two resolved (10h and 30h) inside the window, one reopened once
        Ticket inProgress = data.ticket(org, requester, "ip");
        inProgress.assignTo(tech); inProgress.start(now); tickets.save(inProgress);
        Ticket r1 = resolved(org, requester, tech, now.minus(5, ChronoUnit.DAYS), 10);
        Ticket r2 = resolved(org, requester, tech, now.minus(3, ChronoUnit.DAYS), 30);
        jdbc.sql("UPDATE tickets SET reopen_count = 1 WHERE id = :id").param("id", r2.getId()).update();
        // tech2: one open assigned, one resolved outside the window
        Ticket open = data.ticket(org, requester, "open"); open.assignTo(tech2); tickets.save(open);
        resolved(org, requester, tech2, now.minus(60, ChronoUnit.DAYS), 5);
        // unassigned open ticket + noise in another organization
        data.ticket(org, requester, "unassigned");
        data.ticket(other, tech, "other org");

        MockHttpSession session = api.login(admin.getEmail(), TestData.PASSWORD);
        mvc.perform(get("/api/organizations/" + org.getId() + "/reports/summary")
                        .param("from", now.minus(30, ChronoUnit.DAYS).toString())
                        .param("to", now.toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTickets").value(3))
                .andExpect(jsonPath("$.unassignedTickets").value(1))
                .andExpect(jsonPath("$.byStatus.OPEN").value(2))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.RESOLVED").value(3))
                .andExpect(jsonPath("$.byStatus.CLOSED").value(0))
                .andExpect(jsonPath("$.byPriority.MEDIUM").value(3))
                .andExpect(jsonPath("$.resolvedInPeriod").value(2))
                .andExpect(jsonPath("$.avgResolutionHours").value(20.0))
                .andExpect(jsonPath("$.reopenedTickets").value(1))
                .andExpect(jsonPath("$.workload[0].fullName").value(tech.getFullName()))
                .andExpect(jsonPath("$.workload[0].activeAssigned").value(1))
                .andExpect(jsonPath("$.workload[0].inProgress").value(1))
                .andExpect(jsonPath("$.workload[0].resolvedInPeriod").value(2))
                .andExpect(jsonPath("$.workload[0].avgResolutionHours").value(20.0))
                .andExpect(jsonPath("$.workload[1].fullName").value(tech2.getFullName()))
                .andExpect(jsonPath("$.workload[1].resolvedInPeriod").value(0))
                .andExpect(jsonPath("$.workload[1].avgResolutionHours").doesNotExist())
                .andExpect(jsonPath("$.workload[2].fullName").value(admin.getFullName()));

        MockHttpSession requesterSession = api.login(requester.getEmail(), TestData.PASSWORD);
        mvc.perform(get("/api/organizations/" + org.getId() + "/reports/summary").session(requesterSession))
                .andExpect(status().isForbidden());
    }

    private Ticket resolved(Organization org, User requester, User tech, Instant createdAt, int hours) {
        Ticket t = data.ticket(org, requester, "resolved");
        t.assignTo(tech);
        t.start(createdAt);
        t.resolve(createdAt.plus(hours, ChronoUnit.HOURS));
        t = tickets.saveAndFlush(t);
        jdbc.sql("UPDATE tickets SET created_at = :c WHERE id = :id")
                .param("c", Timestamp.from(createdAt)).param("id", t.getId()).update();
        return t;
    }
}

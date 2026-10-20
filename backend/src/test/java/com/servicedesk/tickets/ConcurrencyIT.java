package com.servicedesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Organization;
import com.servicedesk.support.ApiIntegrationTest;
import com.servicedesk.support.TestData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two technicians edit the same ticket at the same time: exactly one update wins and the other
 * receives 409 Conflict instead of silently overwriting.
 */
class ConcurrencyIT extends ApiIntegrationTest {

    @Autowired TicketRepository tickets;
    @Autowired TransactionTemplate tx;

    Organization org;
    User admin, tech;
    Ticket ticket;
    String url;

    @BeforeEach
    void seed() {
        org = data.org("Acme");
        admin = data.user("admin");
        tech = data.user("tech");
        data.member(admin, org, MemberRole.ADMIN);
        data.member(tech, org, MemberRole.TECHNICIAN);
        ticket = data.ticket(org, admin, "Contended");
        url = "/api/organizations/" + org.getId() + "/tickets/" + ticket.getId();
    }

    @Test
    void staleVersionFromTheApiIsRejectedWith409() throws Exception {
        MockHttpSession adminSession = api.login(admin.getEmail(), TestData.PASSWORD);
        MockHttpSession techSession = api.login(tech.getEmail(), TestData.PASSWORD);

        // Both clients loaded version 0. Admin's change lands first.
        mvc.perform(api.jsonBody(api.as(adminSession, patch(url)), Map.of("version", 0, "priority", "URGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Technician submits on top of version 0 → conflict, nothing applied.
        mvc.perform(api.jsonBody(api.as(techSession, patch(url)), Map.of("version", 0, "assigneeId", tech.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        Ticket reloaded = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(reloaded.getAssignee()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void trulyConcurrentTransactionsAreSerializedByTheVersionColumn() throws Exception {
        CountDownLatch bothLoaded = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = pool.invokeAll(List.of(
                    () -> attempt(bothLoaded, TicketPriority.LOW),
                    () -> attempt(bothLoaded, TicketPriority.HIGH)));
            List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(outcomes).containsExactlyInAnyOrder("ok", "conflict");
        } finally {
            pool.shutdownNow();
        }
        Ticket reloaded = tickets.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    private String attempt(CountDownLatch bothLoaded, TicketPriority priority) {
        try {
            return tx.execute(status -> {
                Ticket t = tickets.findById(ticket.getId()).orElseThrow();
                bothLoaded.countDown();
                await(bothLoaded);
                t.changePriority(priority);
                tickets.saveAndFlush(t);
                return "ok";
            });
        } catch (OptimisticLockingFailureException e) {
            return "conflict";
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

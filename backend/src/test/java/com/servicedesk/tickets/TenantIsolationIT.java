package com.servicedesk.tickets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Organization;
import com.servicedesk.support.ApiIntegrationTest;
import com.servicedesk.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/**
 * An organization must never see or touch another organization's data, even when the caller
 * guesses valid identifiers. Also covers in-organization visibility rules for requesters.
 */
class TenantIsolationIT extends ApiIntegrationTest {

    Organization acme;
    Organization globex;
    User acmeAdmin;
    User globexAdmin;
    User acmeRequester;
    User acmeOtherRequester;
    Ticket acmeTicket;

    @BeforeEach
    void seed() {
        acme = data.org("Acme");
        globex = data.org("Globex");
        acmeAdmin = data.user("acme-admin");
        globexAdmin = data.user("globex-admin");
        acmeRequester = data.user("acme-req");
        acmeOtherRequester = data.user("acme-req2");
        data.member(acmeAdmin, acme, MemberRole.ADMIN);
        data.member(globexAdmin, globex, MemberRole.ADMIN);
        data.member(acmeRequester, acme, MemberRole.REQUESTER);
        data.member(acmeOtherRequester, acme, MemberRole.REQUESTER);
        acmeTicket = data.ticket(acme, acmeRequester, "Acme only");
    }

    @Test
    void outsiderCannotReadListOrUpdateAnotherOrganizationsTickets() throws Exception {
        MockHttpSession outsider = api.login(globexAdmin.getEmail(), TestData.PASSWORD);
        String base = "/api/organizations/" + acme.getId() + "/tickets";

        mvc.perform(get(base).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/" + acmeTicket.getId()).session(outsider)).andExpect(status().isNotFound());
        mvc.perform(api.jsonBody(api.as(outsider, patch(base + "/" + acmeTicket.getId())),
                        Map.of("version", 0, "priority", "URGENT")))
                .andExpect(status().isNotFound());
        mvc.perform(api.jsonBody(api.as(outsider, post(base + "/" + acmeTicket.getId() + "/comments")),
                        Map.of("body", "sneaky")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/organizations/" + acme.getId() + "/reports/summary").session(outsider))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/organizations/" + acme.getId() + "/members").session(outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketIdFromAnotherOrganizationIsNotFoundEvenForAdmins() throws Exception {
        // Globex admin uses their own organization in the path but Acme's ticket id.
        MockHttpSession outsider = api.login(globexAdmin.getEmail(), TestData.PASSWORD);
        mvc.perform(get("/api/organizations/" + globex.getId() + "/tickets/" + acmeTicket.getId()).session(outsider))
                .andExpect(status().isNotFound());
        mvc.perform(api.jsonBody(api.as(outsider, patch("/api/organizations/" + globex.getId() + "/tickets/" + acmeTicket.getId())),
                        Map.of("version", 0, "priority", "URGENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requesterOnlySeesOwnTickets() throws Exception {
        data.ticket(acme, acmeOtherRequester, "Someone else's");
        MockHttpSession requester = api.login(acmeRequester.getEmail(), TestData.PASSWORD);
        String base = "/api/organizations/" + acme.getId() + "/tickets";

        mvc.perform(get(base).session(requester))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Acme only"));

        // Even with an explicit filter for another requester, results stay scoped to the caller.
        mvc.perform(get(base).param("requesterId", acmeOtherRequester.getId().toString()).session(requester))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Acme only"));

        MockHttpSession other = api.login(acmeOtherRequester.getEmail(), TestData.PASSWORD);
        mvc.perform(get(base + "/" + acmeTicket.getId()).session(other)).andExpect(status().isNotFound());

        MockHttpSession admin = api.login(acmeAdmin.getEmail(), TestData.PASSWORD);
        mvc.perform(get(base).session(admin)).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void requesterCannotAssignOrChangeStatus() throws Exception {
        MockHttpSession requester = api.login(acmeRequester.getEmail(), TestData.PASSWORD);
        String url = "/api/organizations/" + acme.getId() + "/tickets/" + acmeTicket.getId();
        mvc.perform(api.jsonBody(api.as(requester, patch(url)), Map.of("version", 0, "assigneeId", acmeAdmin.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(api.jsonBody(api.as(requester, patch(url)), Map.of("version", 0, "priority", "URGENT")))
                .andExpect(status().isForbidden());
    }
}

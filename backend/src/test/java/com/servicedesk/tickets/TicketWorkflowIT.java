package com.servicedesk.tickets;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import com.servicedesk.identity.User;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Organization;
import com.servicedesk.support.ApiIntegrationTest;
import com.servicedesk.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class TicketWorkflowIT extends ApiIntegrationTest {

    Organization org;
    User admin, tech, tech2, requester;
    MockHttpSession adminSession, techSession, tech2Session, requesterSession;
    String base;

    @BeforeEach
    void seed() throws Exception {
        org = data.org("Acme");
        admin = data.user("admin");
        tech = data.user("tech");
        tech2 = data.user("tech2");
        requester = data.user("req");
        data.member(admin, org, MemberRole.ADMIN);
        data.member(tech, org, MemberRole.TECHNICIAN);
        data.member(tech2, org, MemberRole.TECHNICIAN);
        data.member(requester, org, MemberRole.REQUESTER);
        adminSession = api.login(admin.getEmail(), TestData.PASSWORD);
        techSession = api.login(tech.getEmail(), TestData.PASSWORD);
        tech2Session = api.login(tech2.getEmail(), TestData.PASSWORD);
        requesterSession = api.login(requester.getEmail(), TestData.PASSWORD);
        base = "/api/organizations/" + org.getId() + "/tickets";
    }

    private JsonNode create(MockHttpSession session, String title) throws Exception {
        return api.read(mvc.perform(api.jsonBody(api.as(session, post(base)),
                        Map.of("title", title, "description", "Something is broken", "priority", "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn());
    }

    @Test
    void fullLifecycleLeavesACompleteHistory() throws Exception {
        long id = create(requesterSession, "Cannot access my account").get("id").asLong();
        String url = base + "/" + id;

        // Admin assigns
        mvc.perform(api.jsonBody(api.as(adminSession, patch(url)), Map.of("version", 0, "assigneeId", tech.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(tech.getId()))
                .andExpect(jsonPath("$.version").value(1));

        // Technician asks for info, starts, resolves
        mvc.perform(api.jsonBody(api.as(techSession, post(url + "/comments")), Map.of("body", "Which browser are you using?")))
                .andExpect(status().isCreated());
        mvc.perform(api.jsonBody(api.as(techSession, patch(url)), Map.of("version", 1, "status", "IN_PROGRESS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(api.jsonBody(api.as(techSession, patch(url)), Map.of("version", 2, "status", "RESOLVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        // Requester confirms and closes
        mvc.perform(api.jsonBody(api.as(requesterSession, patch(url)), Map.of("version", 3, "status", "CLOSED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        mvc.perform(get(url).session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.events[*].type", containsInAnyOrder(
                        "CREATED", "ASSIGNED", "COMMENTED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED")))
                .andExpect(jsonPath("$.events[1].actor.id").value(admin.getId()))
                .andExpect(jsonPath("$.events[5].oldValue").value("RESOLVED"))
                .andExpect(jsonPath("$.events[5].newValue").value("CLOSED"))
                .andExpect(jsonPath("$.permissions.allowedTransitions", hasSize(0)));
    }

    @Test
    void invalidTransitionIs409() throws Exception {
        long id = create(requesterSession, "Skip ahead").get("id").asLong();
        mvc.perform(api.jsonBody(api.as(adminSession, patch(base + "/" + id)), Map.of("version", 0, "status", "RESOLVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void startingWithoutAssigneeIs422() throws Exception {
        long id = create(requesterSession, "No tech yet").get("id").asLong();
        mvc.perform(api.jsonBody(api.as(adminSession, patch(base + "/" + id)), Map.of("version", 0, "status", "IN_PROGRESS")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_REQUIRED"));
    }

    @Test
    void technicianCanClaimUnassignedButNotReassign() throws Exception {
        long id = create(requesterSession, "Claim me").get("id").asLong();
        String url = base + "/" + id;
        mvc.perform(api.jsonBody(api.as(techSession, patch(url)), Map.of("version", 0, "assigneeId", tech.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignee.id").value(tech.getId()));
        // Another technician cannot take it over, nor drive its status.
        mvc.perform(api.jsonBody(api.as(tech2Session, patch(url)), Map.of("version", 1, "assigneeId", tech2.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(api.jsonBody(api.as(tech2Session, patch(url)), Map.of("version", 1, "status", "IN_PROGRESS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAssignToARequester() throws Exception {
        long id = create(requesterSession, "Wrong assignee").get("id").asLong();
        mvc.perform(api.jsonBody(api.as(adminSession, patch(base + "/" + id)), Map.of("version", 0, "assigneeId", requester.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_A_TECHNICIAN"));
    }

    @Test
    void reopenAfterResolveIsCountedAndClearsResolvedAt() throws Exception {
        long id = create(requesterSession, "Came back").get("id").asLong();
        String url = base + "/" + id;
        mvc.perform(api.jsonBody(api.as(adminSession, patch(url)), Map.of("version", 0, "assigneeId", tech.getId(), "status", "IN_PROGRESS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(api.jsonBody(api.as(techSession, patch(url)), Map.of("version", 1, "status", "RESOLVED")))
                .andExpect(status().isOk());
        mvc.perform(api.jsonBody(api.as(requesterSession, patch(url)), Map.of("version", 2, "status", "OPEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.assignee.id").value(tech.getId()));
    }

    @Test
    void listSupportsFiltersPaginationAndSorting() throws Exception {
        for (int i = 1; i <= 7; i++) create(requesterSession, "Ticket " + i);
        long last = create(requesterSession, "Needle in haystack").get("id").asLong();
        mvc.perform(api.jsonBody(api.as(adminSession, patch(base + "/" + last)), Map.of("version", 0, "assigneeId", tech.getId(), "status", "IN_PROGRESS")))
                .andExpect(status().isOk());

        mvc.perform(get(base).param("size", "3").param("page", "1").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(1));

        mvc.perform(get(base).param("status", "IN_PROGRESS").session(adminSession))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Needle in haystack"));

        mvc.perform(get(base).param("assigneeId", tech.getId().toString()).session(techSession))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(base).param("unassigned", "true").session(techSession))
                .andExpect(jsonPath("$.totalElements").value(7));

        mvc.perform(get(base).param("q", "needle").session(adminSession))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(base).param("sort", "title,asc").param("size", "1").session(adminSession))
                .andExpect(jsonPath("$.content[0].title").value("Needle in haystack"));

        mvc.perform(get(base).param("status", "BOGUS").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void detailExposesPermissionsPerRole() throws Exception {
        long id = create(requesterSession, "Perms").get("id").asLong();
        String url = base + "/" + id;
        mvc.perform(get(url).session(techSession))
                .andExpect(jsonPath("$.permissions.canClaim").value(true))
                .andExpect(jsonPath("$.permissions.canAssign").value(false))
                .andExpect(jsonPath("$.permissions.allowedTransitions", hasSize(0)));
        mvc.perform(get(url).session(adminSession))
                .andExpect(jsonPath("$.permissions.canAssign").value(true))
                .andExpect(jsonPath("$.permissions.allowedTransitions", containsInAnyOrder("IN_PROGRESS")));
        mvc.perform(get(url).session(requesterSession))
                .andExpect(jsonPath("$.permissions.canComment").value(true))
                .andExpect(jsonPath("$.permissions.canChangePriority").value(false));
    }
}

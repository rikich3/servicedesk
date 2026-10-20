package com.servicedesk.organizations;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.identity.User;
import com.servicedesk.support.ApiIntegrationTest;
import com.servicedesk.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembersIT extends ApiIntegrationTest {

    @Test
    void creatorBecomesAdminAndCanManageMembers() throws Exception {
        User creator = data.user("creator");
        User newcomer = data.user("newcomer");
        MockHttpSession session = api.login(creator.getEmail(), TestData.PASSWORD);

        long orgId = api.read(mvc.perform(api.jsonBody(api.as(session, post("/api/organizations")),
                        Map.of("name", "Acme Corp", "slug", "acme-corp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn()).get("id").asLong();

        String members = "/api/organizations/" + orgId + "/members";
        mvc.perform(api.jsonBody(api.as(session, post(members)), Map.of("email", newcomer.getEmail(), "role", "TECHNICIAN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TECHNICIAN"));
        mvc.perform(api.jsonBody(api.as(session, post(members)), Map.of("email", newcomer.getEmail(), "role", "TECHNICIAN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        mvc.perform(api.jsonBody(api.as(session, post(members)), Map.of("email", "ghost@test.local", "role", "REQUESTER")))
                .andExpect(status().isNotFound());

        mvc.perform(get(members).session(session)).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(api.jsonBody(api.as(session, patch(members + "/" + newcomer.getId())), Map.of("role", "ADMIN")))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Now the creator can step down because another admin exists.
        mvc.perform(api.jsonBody(api.as(session, patch(members + "/" + creator.getId())), Map.of("role", "REQUESTER")))
                .andExpect(status().isOk());
        // ...but is no longer allowed to manage members.
        mvc.perform(api.as(session, delete(members + "/" + newcomer.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void lastAdminCannotBeDemotedOrRemoved() throws Exception {
        User admin = data.user("admin");
        Organization org = data.org("Solo");
        data.member(admin, org, MemberRole.ADMIN);
        MockHttpSession session = api.login(admin.getEmail(), TestData.PASSWORD);
        String me = "/api/organizations/" + org.getId() + "/members/" + admin.getId();

        mvc.perform(api.jsonBody(api.as(session, patch(me)), Map.of("role", "TECHNICIAN")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        mvc.perform(api.as(session, delete(me)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void technicianCannotAddMembers() throws Exception {
        User admin = data.user("admin");
        User tech = data.user("tech");
        Organization org = data.org("Acme");
        data.member(admin, org, MemberRole.ADMIN);
        data.member(tech, org, MemberRole.TECHNICIAN);
        MockHttpSession session = api.login(tech.getEmail(), TestData.PASSWORD);
        mvc.perform(api.jsonBody(api.as(session, post("/api/organizations/" + org.getId() + "/members")),
                        Map.of("email", admin.getEmail(), "role", "REQUESTER")))
                .andExpect(status().isForbidden());
    }
}

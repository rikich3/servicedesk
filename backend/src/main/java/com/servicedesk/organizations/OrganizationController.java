package com.servicedesk.organizations;

import com.servicedesk.identity.AppUserPrincipal;
import com.servicedesk.organizations.OrganizationDtos.AddMemberRequest;
import com.servicedesk.organizations.OrganizationDtos.ChangeRoleRequest;
import com.servicedesk.organizations.OrganizationDtos.CreateOrganizationRequest;
import com.servicedesk.organizations.OrganizationDtos.MemberResponse;
import com.servicedesk.organizations.OrganizationDtos.OrganizationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@AuthenticationPrincipal AppUserPrincipal me,
                                       @Valid @RequestBody CreateOrganizationRequest request) {
        return service.create(me.getId(), request);
    }

    @GetMapping
    public List<OrganizationResponse> listMine(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.listMine(me.getId());
    }

    @GetMapping("/{orgId}")
    public OrganizationResponse get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId) {
        return service.get(orgId, me.getId());
    }

    @GetMapping("/{orgId}/members")
    public List<MemberResponse> members(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId) {
        return service.listMembers(orgId, me.getId());
    }

    @PostMapping("/{orgId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                    @Valid @RequestBody AddMemberRequest request) {
        return service.addMember(orgId, me.getId(), request);
    }

    @PatchMapping("/{orgId}/members/{userId}")
    public MemberResponse changeRole(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                     @PathVariable Long userId, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(orgId, me.getId(), userId, request);
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                             @PathVariable Long userId) {
        service.removeMember(orgId, me.getId(), userId);
    }
}

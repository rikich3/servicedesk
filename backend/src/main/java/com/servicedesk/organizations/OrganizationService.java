package com.servicedesk.organizations;

import com.servicedesk.common.BusinessRuleException;
import com.servicedesk.common.ConflictException;
import com.servicedesk.common.NotFoundException;
import com.servicedesk.identity.User;
import com.servicedesk.identity.UserRepository;
import com.servicedesk.organizations.OrganizationDtos.AddMemberRequest;
import com.servicedesk.organizations.OrganizationDtos.ChangeRoleRequest;
import com.servicedesk.organizations.OrganizationDtos.CreateOrganizationRequest;
import com.servicedesk.organizations.OrganizationDtos.MemberResponse;
import com.servicedesk.organizations.OrganizationDtos.OrganizationResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AccessGuard guard;

    public OrganizationService(OrganizationRepository organizations, MembershipRepository memberships,
                               UserRepository users, AccessGuard guard) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
        this.guard = guard;
    }

    /** The creator becomes the first administrator of the new workspace. */
    @Transactional
    public OrganizationResponse create(Long creatorId, CreateOrganizationRequest request) {
        if (organizations.existsBySlug(request.slug().toLowerCase())) {
            throw new ConflictException("SLUG_TAKEN", "An organization with this slug already exists");
        }
        User creator = users.findById(creatorId).orElseThrow(() -> new NotFoundException("User not found"));
        Organization organization = organizations.save(new Organization(request.name(), request.slug()));
        Membership membership = memberships.save(new Membership(creator, organization, MemberRole.ADMIN));
        return OrganizationResponse.from(membership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMine(Long userId) {
        return memberships.findAllByUserIdWithOrganization(userId).stream().map(OrganizationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(Long organizationId, Long userId) {
        return OrganizationResponse.from(guard.requireMember(organizationId, userId));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long organizationId, Long userId) {
        guard.requireMember(organizationId, userId);
        return memberships.findAllByOrganizationIdWithUser(organizationId).stream().map(MemberResponse::from).toList();
    }

    /** Links an existing account to the organization. Invitations by email are out of scope for the MVP. */
    @Transactional
    public MemberResponse addMember(Long organizationId, Long actorId, AddMemberRequest request) {
        Membership actor = guard.requireRole(organizationId, actorId, MemberRole.ADMIN);
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new NotFoundException("No account exists with email " + request.email()));
        if (memberships.findByUserIdAndOrganizationId(user.getId(), organizationId).isPresent()) {
            throw new ConflictException("ALREADY_MEMBER", "This user is already a member of the organization");
        }
        Membership membership = memberships.save(new Membership(user, actor.getOrganization(), request.role()));
        return MemberResponse.from(membership);
    }

    @Transactional
    public MemberResponse changeRole(Long organizationId, Long actorId, Long targetUserId, ChangeRoleRequest request) {
        guard.requireRole(organizationId, actorId, MemberRole.ADMIN);
        Membership target = memberships.findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        if (target.isAdmin() && request.role() != MemberRole.ADMIN) {
            ensureNotLastAdmin(organizationId);
        }
        target.changeRole(request.role());
        return MemberResponse.from(target);
    }

    @Transactional
    public void removeMember(Long organizationId, Long actorId, Long targetUserId) {
        guard.requireRole(organizationId, actorId, MemberRole.ADMIN);
        Membership target = memberships.findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        if (target.isAdmin()) {
            ensureNotLastAdmin(organizationId);
        }
        memberships.delete(target);
    }

    private void ensureNotLastAdmin(Long organizationId) {
        if (memberships.countByOrganizationIdAndRole(organizationId, MemberRole.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ADMIN", "An organization must keep at least one administrator");
        }
    }
}

package com.servicedesk.organizations;

import com.servicedesk.common.ForbiddenException;
import com.servicedesk.common.NotFoundException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tenant isolation policy. Every service operation resolves the caller's membership in the
 * organization named in the URL before touching data. A non-member gets 404 (we do not reveal
 * that the organization exists); a member without the required role gets 403.
 */
@Component
public class AccessGuard {

    private final MembershipRepository memberships;

    public AccessGuard(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    public Membership requireMember(Long organizationId, Long userId) {
        return memberships.findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    public Membership requireRole(Long organizationId, Long userId, MemberRole first, MemberRole... rest) {
        Membership membership = requireMember(organizationId, userId);
        Set<MemberRole> allowed = EnumSet.of(first, rest);
        if (!allowed.contains(membership.getRole())) {
            throw new ForbiddenException("This action requires role " + allowed);
        }
        return membership;
    }
}

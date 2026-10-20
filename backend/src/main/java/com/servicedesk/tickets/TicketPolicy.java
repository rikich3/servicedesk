package com.servicedesk.tickets;

import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Membership;
import com.servicedesk.tickets.TicketDtos.Permissions;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Authorization rules for tickets, expressed per role. Kept separate from HTTP concerns so the
 * rules are unit-testable and reusable by both the write path and the "what can I do" response.
 *
 * <ul>
 *   <li>REQUESTER: sees and comments on own tickets; may close or reopen a RESOLVED ticket.</li>
 *   <li>TECHNICIAN: sees every ticket; may claim unassigned ones; drives tickets assigned to them.</li>
 *   <li>ADMIN: everything, including assignment to any technician.</li>
 * </ul>
 */
@Component
public class TicketPolicy {

    public boolean canView(Membership m, Ticket t) {
        return m.getRole() != MemberRole.REQUESTER || t.isRequestedBy(m.getUser().getId());
    }

    public boolean canAssign(Membership m) {
        return m.isAdmin();
    }

    public boolean canClaim(Membership m, Ticket t) {
        return m.isTechnician() && t.getAssignee() == null && t.getStatus().isActive();
    }

    public boolean canChangePriority(Membership m, Ticket t) {
        Long userId = m.getUser().getId();
        return m.isAdmin() || (m.isTechnician() && t.isAssignedTo(userId));
    }

    public Set<TicketStatus> allowedTransitions(Membership m, Ticket t) {
        Long userId = m.getUser().getId();
        Set<TicketStatus> byState = t.getStatus().allowedTargets();
        Set<TicketStatus> byRole = switch (m.getRole()) {
            case ADMIN -> EnumSet.allOf(TicketStatus.class);
            // Assigned technicians drive active tickets; reopening a resolved one is for the requester or an admin.
            case TECHNICIAN -> t.isAssignedTo(userId) && t.getStatus().isActive()
                    ? EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.OPEN)
                    : EnumSet.noneOf(TicketStatus.class);
            case REQUESTER -> t.isRequestedBy(userId) && t.getStatus() == TicketStatus.RESOLVED
                    ? EnumSet.of(TicketStatus.CLOSED, TicketStatus.OPEN)
                    : EnumSet.noneOf(TicketStatus.class);
        };
        Set<TicketStatus> result = EnumSet.noneOf(TicketStatus.class);
        for (TicketStatus s : byState) {
            if (byRole.contains(s)) result.add(s);
        }
        return result;
    }

    public Permissions permissions(Membership m, Ticket t) {
        return new Permissions(allowedTransitions(m, t), canAssign(m) && t.getStatus().isActive(),
                canClaim(m, t), canChangePriority(m, t), canView(m, t));
    }
}

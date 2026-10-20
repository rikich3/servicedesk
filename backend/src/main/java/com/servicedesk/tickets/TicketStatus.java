package com.servicedesk.tickets;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ticket lifecycle. The transition table is the single source of truth; {@link Ticket}
 * consults it before changing state.
 *
 * <pre>
 *   OPEN ⇄ IN_PROGRESS → RESOLVED → CLOSED
 *                           └──reopen──→ OPEN
 * </pre>
 * CLOSED is terminal: a closed ticket is never reopened, a new one is created instead.
 */
public enum TicketStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(RESOLVED, OPEN),
            RESOLVED, EnumSet.of(CLOSED, OPEN),
            CLOSED, EnumSet.noneOf(TicketStatus.class));

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<TicketStatus> allowedTargets() {
        return ALLOWED.get(this);
    }

    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }
}

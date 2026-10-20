package com.servicedesk.tickets;

import com.servicedesk.common.ConflictException;

public class InvalidTransitionException extends ConflictException {
    public InvalidTransitionException(TicketStatus from, TicketStatus to) {
        super("INVALID_TRANSITION", "Cannot move a ticket from " + from + " to " + to);
    }
}

package com.servicedesk.tickets;

import org.springframework.data.jpa.domain.Specification;

/** Composable WHERE clauses for the ticket list. Organization is always the first predicate. */
final class TicketSpecifications {
    private TicketSpecifications() {}

    static Specification<Ticket> inOrganization(Long organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    static Specification<Ticket> hasStatus(TicketStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> hasPriority(TicketPriority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    static Specification<Ticket> assignedTo(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("assignee").get("id"), userId);
    }

    static Specification<Ticket> unassigned(Boolean unassigned) {
        return Boolean.TRUE.equals(unassigned) ? (root, query, cb) -> cb.isNull(root.get("assignee")) : null;
    }

    static Specification<Ticket> requestedBy(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId);
    }

    static Specification<Ticket> titleContains(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }
}

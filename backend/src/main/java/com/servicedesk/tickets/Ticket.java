package com.servicedesk.tickets;

import com.servicedesk.common.BusinessRuleException;
import com.servicedesk.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Aggregate root for a support request. State changes go through intention-revealing
 * methods so the lifecycle rules live here, not in controllers or services.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", updatable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** Optimistic locking: a stale client update is rejected with 409 instead of silently winning. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Ticket() {}

    public Ticket(Long organizationId, String title, String description, TicketPriority priority, User requester) {
        this.organizationId = organizationId;
        this.title = title.trim();
        this.description = description.trim();
        this.priority = priority;
        this.requester = requester;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    // ----- lifecycle -----

    public void transitionTo(TicketStatus target, Instant now) {
        switch (target) {
            case IN_PROGRESS -> start(now);
            case RESOLVED -> resolve(now);
            case CLOSED -> close(now);
            case OPEN -> {
                if (status == TicketStatus.RESOLVED) reopen(now);
                else returnToQueue(now);
            }
        }
    }

    public void start(Instant now) {
        require(TicketStatus.IN_PROGRESS);
        if (assignee == null) {
            throw new BusinessRuleException("ASSIGNEE_REQUIRED", "Assign a technician before starting work");
        }
        status = TicketStatus.IN_PROGRESS;
    }

    public void resolve(Instant now) {
        require(TicketStatus.RESOLVED);
        status = TicketStatus.RESOLVED;
        resolvedAt = now;
    }

    public void close(Instant now) {
        require(TicketStatus.CLOSED);
        status = TicketStatus.CLOSED;
        closedAt = now;
    }

    /** Reopening keeps the assignee (they have the context) but clears the resolution timestamp. */
    public void reopen(Instant now) {
        if (status != TicketStatus.RESOLVED) throw new InvalidTransitionException(status, TicketStatus.OPEN);
        status = TicketStatus.OPEN;
        resolvedAt = null;
        reopenCount++;
    }

    /** IN_PROGRESS → OPEN: the technician hands the ticket back to the queue. */
    public void returnToQueue(Instant now) {
        if (status != TicketStatus.IN_PROGRESS) throw new InvalidTransitionException(status, TicketStatus.OPEN);
        status = TicketStatus.OPEN;
        assignee = null;
    }

    private void require(TicketStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);
        }
    }

    public void assignTo(User technician) {
        if (!status.isActive()) {
            throw new BusinessRuleException("TICKET_NOT_ACTIVE", "Only open or in-progress tickets can be assigned");
        }
        this.assignee = technician;
    }

    public void unassign() {
        if (status == TicketStatus.IN_PROGRESS) {
            throw new BusinessRuleException("IN_PROGRESS_NEEDS_ASSIGNEE",
                    "Return the ticket to the queue instead of unassigning it while in progress");
        }
        this.assignee = null;
    }

    public void changePriority(TicketPriority newPriority) {
        this.priority = newPriority;
    }

    public boolean isAssignedTo(Long userId) {
        return assignee != null && assignee.getId().equals(userId);
    }

    public boolean isRequestedBy(Long userId) {
        return requester.getId().equals(userId);
    }

    // ----- getters -----
    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public TicketPriority getPriority() { return priority; }
    public User getRequester() { return requester; }
    public User getAssignee() { return assignee; }
    public Long getVersion() { return version; }
    public int getReopenCount() { return reopenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
}

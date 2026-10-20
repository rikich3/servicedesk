package com.servicedesk.tickets;

import com.servicedesk.identity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class TicketDtos {
    private TicketDtos() {}

    public record UserRef(Long id, String fullName) {
        static UserRef of(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getFullName());
        }
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 10_000) String description,
            @NotNull TicketPriority priority) {}

    /**
     * Partial update. {@code version} is mandatory: the client states which version it saw, and
     * the server refuses to apply changes on top of a newer one.
     */
    public record UpdateTicketRequest(
            @NotNull Long version,
            TicketStatus status,
            TicketPriority priority,
            Long assigneeId,
            Boolean unassign) {}

    public record CreateCommentRequest(@NotBlank @Size(max = 5_000) String body) {}

    public record TicketListFilter(TicketStatus status, TicketPriority priority, Long assigneeId,
                                   Boolean unassigned, Long requesterId, String q) {}

    public record TicketResponse(Long id, String title, String description, TicketStatus status,
                                 TicketPriority priority, UserRef requester, UserRef assignee, Long version,
                                 int reopenCount, Instant createdAt, Instant updatedAt, Instant resolvedAt,
                                 Instant closedAt) {
        static TicketResponse from(Ticket t) {
            return new TicketResponse(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                    UserRef.of(t.getRequester()), UserRef.of(t.getAssignee()), t.getVersion(), t.getReopenCount(),
                    t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(), t.getClosedAt());
        }
    }

    public record CommentResponse(Long id, UserRef author, String body, Instant createdAt) {
        static CommentResponse from(Comment c) {
            return new CommentResponse(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), c.getCreatedAt());
        }
    }

    public record EventResponse(Long id, UserRef actor, TicketEventType type, String oldValue, String newValue,
                                Instant createdAt) {
        static EventResponse from(TicketEvent e) {
            return new EventResponse(e.getId(), UserRef.of(e.getActor()), e.getType(), e.getOldValue(),
                    e.getNewValue(), e.getCreatedAt());
        }
    }

    /** What the caller is allowed to do next; lets the UI render only valid actions. */
    public record Permissions(Set<TicketStatus> allowedTransitions, boolean canAssign, boolean canClaim,
                              boolean canChangePriority, boolean canComment) {}

    public record TicketDetailResponse(TicketResponse ticket, List<CommentResponse> comments,
                                       List<EventResponse> events, Permissions permissions) {}
}

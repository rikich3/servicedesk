package com.servicedesk.tickets;

import com.servicedesk.common.BusinessRuleException;
import com.servicedesk.common.ConflictException;
import com.servicedesk.common.ForbiddenException;
import com.servicedesk.common.NotFoundException;
import com.servicedesk.common.PageResponse;
import com.servicedesk.identity.User;
import com.servicedesk.organizations.AccessGuard;
import com.servicedesk.organizations.MemberRole;
import com.servicedesk.organizations.Membership;
import com.servicedesk.organizations.MembershipRepository;
import com.servicedesk.tickets.TicketDtos.CommentResponse;
import com.servicedesk.tickets.TicketDtos.CreateCommentRequest;
import com.servicedesk.tickets.TicketDtos.CreateTicketRequest;
import com.servicedesk.tickets.TicketDtos.EventResponse;
import com.servicedesk.tickets.TicketDtos.TicketDetailResponse;
import com.servicedesk.tickets.TicketDtos.TicketListFilter;
import com.servicedesk.tickets.TicketDtos.TicketResponse;
import com.servicedesk.tickets.TicketDtos.UpdateTicketRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "priority", "status", "title");
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final TicketEventRepository events;
    private final MembershipRepository memberships;
    private final AccessGuard guard;
    private final TicketPolicy policy;
    private final Clock clock;

    public TicketService(TicketRepository tickets, CommentRepository comments, TicketEventRepository events,
                         MembershipRepository memberships, AccessGuard guard, TicketPolicy policy, Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.events = events;
        this.memberships = memberships;
        this.guard = guard;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public TicketResponse create(Long organizationId, Long userId, CreateTicketRequest request) {
        Membership member = guard.requireMember(organizationId, userId);
        Ticket ticket = tickets.save(new Ticket(organizationId, request.title(), request.description(),
                request.priority(), member.getUser()));
        events.save(new TicketEvent(ticket, member.getUser(), TicketEventType.CREATED, null, ticket.getStatus().name()));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> list(Long organizationId, Long userId, TicketListFilter filter,
                                             int page, int size, String sort) {
        Membership member = guard.requireMember(organizationId, userId);
        // Absent filters yield null specifications; only the present ones are AND-ed together.
        Specification<Ticket> spec = Specification.allOf(Stream.of(
                TicketSpecifications.inOrganization(organizationId),
                TicketSpecifications.hasStatus(filter.status()),
                TicketSpecifications.hasPriority(filter.priority()),
                TicketSpecifications.assignedTo(filter.assigneeId()),
                TicketSpecifications.unassigned(filter.unassigned()),
                // Requesters only ever see their own tickets, whatever the filter says.
                TicketSpecifications.requestedBy(member.getRole() == MemberRole.REQUESTER ? userId : filter.requesterId()),
                TicketSpecifications.titleContains(filter.q()))
                .filter(Objects::nonNull)
                .toList());
        Page<Ticket> result = tickets.findAll(spec, pageable(page, size, sort));
        return PageResponse.of(result, TicketResponse::from);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse get(Long organizationId, Long userId, Long ticketId) {
        Membership member = guard.requireMember(organizationId, userId);
        Ticket ticket = loadVisible(organizationId, ticketId, member);
        List<CommentResponse> commentList = comments.findAllByTicketId(ticketId).stream().map(CommentResponse::from).toList();
        List<EventResponse> eventList = events.findAllByTicketId(ticketId).stream().map(EventResponse::from).toList();
        return new TicketDetailResponse(TicketResponse.from(ticket), commentList, eventList,
                policy.permissions(member, ticket));
    }

    /**
     * Applies assignment, priority and status changes in that order. The ticket row and its
     * history entries are written in one transaction: either all of it is visible or none.
     */
    @Transactional
    public TicketResponse update(Long organizationId, Long userId, Long ticketId, UpdateTicketRequest request) {
        Membership member = guard.requireMember(organizationId, userId);
        Ticket ticket = loadVisible(organizationId, ticketId, member);
        User actor = member.getUser();
        Instant now = clock.instant();

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new ConflictException("STALE_VERSION",
                    "The ticket was modified by someone else. Reload and try again.");
        }

        if (Boolean.TRUE.equals(request.unassign())) {
            if (!policy.canAssign(member)) throw new ForbiddenException("Only administrators can unassign tickets");
            if (ticket.getAssignee() != null) {
                String previous = ticket.getAssignee().getFullName();
                ticket.unassign();
                events.save(new TicketEvent(ticket, actor, TicketEventType.UNASSIGNED, previous, null));
            }
        } else if (request.assigneeId() != null) {
            assign(member, ticket, request.assigneeId(), actor);
        }

        if (request.priority() != null && request.priority() != ticket.getPriority()) {
            if (!policy.canChangePriority(member, ticket)) {
                throw new ForbiddenException("You cannot change the priority of this ticket");
            }
            String previous = ticket.getPriority().name();
            ticket.changePriority(request.priority());
            events.save(new TicketEvent(ticket, actor, TicketEventType.PRIORITY_CHANGED, previous, request.priority().name()));
        }

        if (request.status() != null && request.status() != ticket.getStatus()) {
            if (!ticket.getStatus().canTransitionTo(request.status())) {
                throw new InvalidTransitionException(ticket.getStatus(), request.status());
            }
            if (!policy.allowedTransitions(member, ticket).contains(request.status())) {
                throw new ForbiddenException("Your role cannot move this ticket to " + request.status());
            }
            String previous = ticket.getStatus().name();
            ticket.transitionTo(request.status(), now);
            events.save(new TicketEvent(ticket, actor, TicketEventType.STATUS_CHANGED, previous, request.status().name()));
        }

        tickets.flush(); // surface optimistic-lock failures inside the transaction boundary
        return TicketResponse.from(ticket);
    }

    @Transactional
    public CommentResponse addComment(Long organizationId, Long userId, Long ticketId, CreateCommentRequest request) {
        Membership member = guard.requireMember(organizationId, userId);
        Ticket ticket = loadVisible(organizationId, ticketId, member);
        Comment comment = comments.save(new Comment(ticket, member.getUser(), request.body()));
        events.save(new TicketEvent(ticket, member.getUser(), TicketEventType.COMMENTED, null,
                "comment:" + comment.getId()));
        return CommentResponse.from(comment);
    }

    // ----- helpers -----

    private void assign(Membership member, Ticket ticket, Long assigneeId, User actor) {
        boolean selfClaim = assigneeId.equals(actor.getId()) && policy.canClaim(member, ticket);
        if (!policy.canAssign(member) && !selfClaim) {
            throw new ForbiddenException("Only administrators can assign tickets; technicians may claim unassigned ones");
        }
        Membership target = memberships.findByUserIdAndOrganizationId(assigneeId, ticket.getOrganizationId())
                .orElseThrow(() -> new NotFoundException("Assignee is not a member of this organization"));
        if (!target.canBeAssigned()) {
            throw new BusinessRuleException("NOT_A_TECHNICIAN", "Tickets can only be assigned to technicians or admins");
        }
        if (ticket.isAssignedTo(assigneeId)) return;
        String previous = ticket.getAssignee() == null ? null : ticket.getAssignee().getFullName();
        ticket.assignTo(target.getUser());
        events.save(new TicketEvent(ticket, actor, TicketEventType.ASSIGNED, previous, target.getUser().getFullName()));
    }

    private Ticket loadVisible(Long organizationId, Long ticketId, Membership member) {
        Ticket ticket = tickets.findByIdInOrganization(ticketId, organizationId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!policy.canView(member, ticket)) {
            throw new NotFoundException("Ticket not found");
        }
        return ticket;
    }

    private static Pageable pageable(int page, int size, String sort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",");
        String property = SORTABLE.contains(parts[0]) ? parts[0] : "createdAt";
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, property).and(Sort.by("id")));
    }
}

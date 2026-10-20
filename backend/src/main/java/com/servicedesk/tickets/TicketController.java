package com.servicedesk.tickets;

import com.servicedesk.common.PageResponse;
import com.servicedesk.identity.AppUserPrincipal;
import com.servicedesk.tickets.TicketDtos.CommentResponse;
import com.servicedesk.tickets.TicketDtos.CreateCommentRequest;
import com.servicedesk.tickets.TicketDtos.CreateTicketRequest;
import com.servicedesk.tickets.TicketDtos.EventResponse;
import com.servicedesk.tickets.TicketDtos.TicketDetailResponse;
import com.servicedesk.tickets.TicketDtos.TicketListFilter;
import com.servicedesk.tickets.TicketDtos.TicketResponse;
import com.servicedesk.tickets.TicketDtos.UpdateTicketRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{orgId}/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                 @Valid @RequestBody CreateTicketRequest request) {
        return service.create(orgId, me.getId(), request);
    }

    @GetMapping
    public PageResponse<TicketResponse> list(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                             @RequestParam(required = false) TicketStatus status,
                                             @RequestParam(required = false) TicketPriority priority,
                                             @RequestParam(required = false) Long assigneeId,
                                             @RequestParam(required = false) Boolean unassigned,
                                             @RequestParam(required = false) Long requesterId,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(defaultValue = "createdAt,desc") String sort) {
        var filter = new TicketListFilter(status, priority, assigneeId, unassigned, requesterId, q);
        return service.list(orgId, me.getId(), filter, page, size, sort);
    }

    @GetMapping("/{ticketId}")
    public TicketDetailResponse get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                    @PathVariable Long ticketId) {
        return service.get(orgId, me.getId(), ticketId);
    }

    @PatchMapping("/{ticketId}")
    public TicketResponse update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                 @PathVariable Long ticketId, @Valid @RequestBody UpdateTicketRequest request) {
        return service.update(orgId, me.getId(), ticketId, request);
    }

    @PostMapping("/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                   @PathVariable Long ticketId, @Valid @RequestBody CreateCommentRequest request) {
        return service.addComment(orgId, me.getId(), ticketId, request);
    }

    @GetMapping("/{ticketId}/events")
    public List<EventResponse> events(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long orgId,
                                      @PathVariable Long ticketId) {
        return service.get(orgId, me.getId(), ticketId).events();
    }
}

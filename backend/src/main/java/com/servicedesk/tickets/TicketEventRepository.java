package com.servicedesk.tickets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

    @Query("select e from TicketEvent e join fetch e.actor where e.ticket.id = :ticketId order by e.createdAt asc, e.id asc")
    List<TicketEvent> findAllByTicketId(@Param("ticketId") Long ticketId);
}

package com.servicedesk.tickets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c join fetch c.author where c.ticket.id = :ticketId order by c.createdAt asc, c.id asc")
    List<Comment> findAllByTicketId(@Param("ticketId") Long ticketId);
}

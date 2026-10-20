package com.servicedesk.tickets;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Organization-scoped lookup: a ticket id from another tenant is simply "not found". */
    @Query("select t from Ticket t left join fetch t.requester left join fetch t.assignee "
            + "where t.id = :id and t.organizationId = :orgId")
    Optional<Ticket> findByIdInOrganization(@Param("id") Long id, @Param("orgId") Long organizationId);

    @Override
    @EntityGraph(attributePaths = {"requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}

package com.servicedesk.organizations;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    @Query("select m from Membership m join fetch m.organization where m.user.id = :userId order by m.organization.name")
    List<Membership> findAllByUserIdWithOrganization(@Param("userId") Long userId);

    @Query("select m from Membership m join fetch m.user where m.organization.id = :orgId order by m.user.fullName")
    List<Membership> findAllByOrganizationIdWithUser(@Param("orgId") Long organizationId);

    long countByOrganizationIdAndRole(Long organizationId, MemberRole role);
}

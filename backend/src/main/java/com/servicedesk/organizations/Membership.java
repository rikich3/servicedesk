package com.servicedesk.organizations;

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
import jakarta.persistence.Table;
import java.time.Instant;

/** Join entity with its own attribute: a user can belong to many organizations with different roles. */
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Membership() {}

    public Membership(User user, Organization organization, MemberRole role) {
        this.user = user;
        this.organization = organization;
        this.role = role;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Organization getOrganization() { return organization; }
    public MemberRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void changeRole(MemberRole newRole) { this.role = newRole; }

    public boolean isAdmin() { return role == MemberRole.ADMIN; }
    public boolean isTechnician() { return role == MemberRole.TECHNICIAN; }
    /** Technicians and admins can be assigned tickets. */
    public boolean canBeAssigned() { return role == MemberRole.TECHNICIAN || role == MemberRole.ADMIN; }
}

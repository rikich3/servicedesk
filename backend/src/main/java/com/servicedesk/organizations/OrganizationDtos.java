package com.servicedesk.organizations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class OrganizationDtos {
    private OrganizationDtos() {}

    public record CreateOrganizationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 2, max = 60)
            @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "must be lowercase letters, digits and dashes")
            String slug) {}

    public record OrganizationResponse(Long id, String name, String slug, String role, Instant createdAt) {
        static OrganizationResponse from(Membership m) {
            Organization o = m.getOrganization();
            return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(), m.getRole().name(), o.getCreatedAt());
        }
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull MemberRole role) {}

    public record ChangeRoleRequest(@NotNull MemberRole role) {}

    public record MemberResponse(Long userId, String email, String fullName, String role, Instant joinedAt) {
        static MemberResponse from(Membership m) {
            return new MemberResponse(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(),
                    m.getRole().name(), m.getCreatedAt());
        }
    }
}

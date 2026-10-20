package com.servicedesk.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record MembershipSummary(Long organizationId, String organizationName, String slug, String role) {}

    public record CurrentUser(Long id, String email, String fullName, List<MembershipSummary> memberships) {}
}

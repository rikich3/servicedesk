package com.servicedesk.identity;

import com.servicedesk.common.ConflictException;
import com.servicedesk.common.NotFoundException;
import com.servicedesk.identity.AuthDtos.CurrentUser;
import com.servicedesk.identity.AuthDtos.MembershipSummary;
import com.servicedesk.identity.AuthDtos.RegisterRequest;
import com.servicedesk.organizations.MembershipRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, MembershipRepository memberships, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CurrentUser register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(request.email(), passwordEncoder.encode(request.password()), request.fullName()));
        return currentUser(user.getId());
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        var summaries = memberships.findAllByUserIdWithOrganization(userId).stream()
                .map(m -> new MembershipSummary(
                        m.getOrganization().getId(), m.getOrganization().getName(),
                        m.getOrganization().getSlug(), m.getRole().name()))
                .toList();
        return new CurrentUser(user.getId(), user.getEmail(), user.getFullName(), summaries);
    }
}

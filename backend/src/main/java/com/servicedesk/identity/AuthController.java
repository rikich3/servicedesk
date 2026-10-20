package com.servicedesk.identity;

import com.servicedesk.identity.AuthDtos.CurrentUser;
import com.servicedesk.identity.AuthDtos.LoginRequest;
import com.servicedesk.identity.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AuthService authService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository,
                          AuthService authService) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUser register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return authService.currentUser(principal.getId());
    }

    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.currentUser(principal.getId());
    }

    /** Touching this endpoint guarantees the XSRF-TOKEN cookie is set before the first mutation. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }
}

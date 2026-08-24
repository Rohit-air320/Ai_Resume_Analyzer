package com.resumeiq.security;

import com.resumeiq.security.JwtService.VerifiedAccessToken;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Turns a {@code Bearer} token into an authenticated request, or leaves the request
 * anonymous and lets the filter chain decide what that means.
 *
 * <p>It never writes a response. A bad token here is not automatically an error — the
 * request might be heading for a public endpoint — so the filter records why verification
 * failed in a request attribute and continues. If the endpoint does require authentication,
 * {@link RestAuthenticationEntryPoint} reads that attribute and produces the 401, which
 * keeps every error in this application in one shape and one place.
 *
 * <p>Deliberately not a Spring bean. Boot registers every {@code Filter} bean in the servlet
 * container's own chain as well, so a {@code @Component} here would run this filter twice per
 * request — once outside the security chain, where nothing consumes what it sets.
 * {@code SecurityConfig} constructs it instead, which is also the only place that should be
 * deciding filter order.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /** Set when a token was present but unusable; read by the entry point for its message. */
    static final String FAILURE_ATTRIBUTE = "resumeiq.auth.failure";
    static final String FAILURE_EXPIRED = "expired";
    static final String FAILURE_INVALID = "invalid";

    private final JwtService jwtService;
    private final UserRepository users;
    private final SecurityContextRepository contextRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users,
                                   SecurityContextRepository contextRepository) {
        this.jwtService = jwtService;
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Optional<String> bearer = bearerToken(request);
        if (bearer.isEmpty() || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            VerifiedAccessToken token = jwtService.verify(bearer.get());
            Optional<User> account = users.findByPublicId(token.subject());
            if (account.isEmpty()) {
                // Signature was valid, so this is a token for an account that has since been
                // deleted. Failing closed here is the reason the identity is loaded per request.
                request.setAttribute(FAILURE_ATTRIBUTE, FAILURE_INVALID);
                chain.doFilter(request, response);
                return;
            }
            authenticate(request, response, AuthenticatedUser.of(account.get()));
        } catch (ExpiredJwtException ex) {
            request.setAttribute(FAILURE_ATTRIBUTE, FAILURE_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token or the exception message: both quote token material.
            log.debug("Rejected a bearer token on {}: {}",
                    request.getRequestURI(), ex.getClass().getSimpleName());
            request.setAttribute(FAILURE_ATTRIBUTE, FAILURE_INVALID);
        }

        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, HttpServletResponse response,
                              AuthenticatedUser principal) {

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(principal.authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Spring Security 6 no longer saves the context implicitly. With a stateless
        // repository this stores nothing, but going through it keeps the filter honest:
        // swapping the repository is then a configuration change, not a code change.
        contextRepository.saveContext(context, request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}

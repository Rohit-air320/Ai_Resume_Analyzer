package com.resumeiq.security;

import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.user.UserRepository;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The filter chain, and the reasoning behind each switch that is flipped.
 *
 * <p><strong>Stateless.</strong> No {@code HttpSession} is created, so nothing has to be
 * replicated if this API runs as more than one instance. Identity arrives on every request as
 * a bearer token and is discarded when the request ends.
 *
 * <p><strong>CSRF disabled — with the reason that makes it safe.</strong> A token-in-a-header
 * API is not vulnerable to CSRF, because a cross-site form cannot set an {@code Authorization}
 * header. The refresh endpoint is different: it authenticates with a cookie, which browsers
 * do attach automatically. What protects it is the cookie's own {@code SameSite=Lax}
 * attribute — a cross-site {@code POST} carries no cookie at all, so the request arrives
 * unauthenticated. That is a deliberate trade: one attribute on one cookie instead of a CSRF
 * token round trip on every mutating call. It also means the frontend and the API have to be
 * same-site in production, which is exactly what the Vite proxy models in development.
 *
 * <p><strong>Two chains.</strong> The H2 console needs framing permission and no CSRF, and it
 * exists only in development. Rather than weaken the API chain with rules that mention it,
 * the console gets its own chain annotated {@code @Profile("dev")} — so in any other profile
 * those relaxations are not configured at all, not merely unused.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Endpoints that must work before anybody has an identity. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/health",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
    };

    /** API documentation. Springdoc can be switched off entirely in a deployment. */
    private static final String[] DOCUMENTATION_ENDPOINTS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
    };

    /**
     * BCrypt, at a cost read from configuration.
     *
     * <p>The cost is the whole point of the algorithm: it is what makes a stolen table of
     * hashes expensive to attack. 12 is around a quarter of a second per hash on a laptop,
     * deliberately slow, and configurable so the test suite can drop it to 4 rather than
     * spend a minute hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder(ResumeIqProperties properties) {
        return new BCryptPasswordEncoder(properties.auth().bcryptStrength());
    }

    /**
     * Where the security context lives during a request.
     *
     * <p>A request attribute rather than the session: the identity is rebuilt from the token
     * on every call, so persisting it would be storing a copy of something already carried.
     * Going through a repository at all is what lets an error or async dispatch still see who
     * the caller was.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new RequestAttributeSecurityContextRepository();
    }

    /**
     * One CORS definition for the whole application.
     *
     * <p>It lives here, not in a {@code WebMvcConfigurer}, because Spring Security's chain
     * runs before Spring MVC: configuring both is how a deployment ends up with a preflight
     * that passes the filter chain and then fails at the handler, or vice versa. Origins are
     * listed explicitly — {@code allowCredentials} makes a wildcard illegal, and the refresh
     * cookie needs credentials.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(ResumeIqProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(properties.cors().allowedOrigins()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Location for created resources, Retry-After for a throttled login.
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * The H2 console, in development only. Ordered first so its rules apply to console
     * requests and nothing about it leaks into the API chain.
     */
    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // The matcher comes from Boot's own configuration, so changing
                // spring.h2.console.path cannot leave this rule pointing at the wrong URL.
                .securityMatcher(PathRequest.toH2Console())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                // The console is a frameset. Same-origin only, never a blanket allow.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository users,
            SecurityContextRepository contextRepository,
            CorsConfigurationSource corsConfigurationSource,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // No browser-facing login form and no basic auth: this API answers with JSON,
                // and a redirect to a login page would be a lie to an XHR caller.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(requests -> requests
                        // Preflight carries no credentials by definition.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // An error dispatch must reach the handler that formats it, or a 500
                        // would come back as a 401 and hide the actual failure.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(DOCUMENTATION_ENDPOINTS).permitAll()
                        // Everything else, including every endpoint added in later phases,
                        // is closed by default. New routes are private unless listed above.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, users, contextRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

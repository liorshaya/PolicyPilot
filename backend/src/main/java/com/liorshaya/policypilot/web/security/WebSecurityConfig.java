package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.web.controller.ApiPaths;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * The front door (Document 2, Security and Demo Protections; Document 5, Authentication, Session and Authorization):
 * deny by default, the health check and the code exchange public, the rate limits, CORS for the allowlisted origins with credentials,
 * the custom header and Origin check, the signed session cookie, Spring Security's headers with HSTS, no HTTP
 * session and no Spring Security CSRF token (the three defenses of Document 5 replace it).
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfig {

    /** How long a browser may cache a CORS preflight answer. */
    static final Duration PREFLIGHT_MAX_AGE = Duration.ofMinutes(10);

    /** The routes that need no cookie: the health check, the code exchange and the container's error page. */
    static RequestMatcher publicRoutes() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher("/actuator/health"),
                paths.matcher("/actuator/health/**"),
                paths.matcher("/error"),
                paths.matcher(HttpMethod.POST, ApiPaths.AUTH_CODE));
    }

    @Bean
    SessionCookies sessionCookies(PolicyPilotProperties properties) {
        return new SessionCookies(properties.cookieSecret());
    }

    @Bean
    AccessCodeVerifier accessCodeVerifier(PolicyPilotProperties properties) {
        return new AccessCodeVerifier(properties.accessCode());
    }

    @Bean
    LoginThrottle loginThrottle() {
        return new LoginThrottle();
    }

    @Bean
    RateLimits rateLimits(Clock clock, PolicyPilotProperties properties) {
        return new RateLimits(clock, properties.rateLimit().perMinute(), properties.rateLimit().perSandboxPerHour());
    }

    /** CORS for the allowlisted origins with credentials (Document 5, CSRF, the first defense). */
    static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, CsrfDefenseFilter.CLIENT_HEADER, "Last-Event-ID"));
        cors.setExposedHeaders(List.of("X-Trace-Id", HttpHeaders.RETRY_AFTER, HttpHeaders.LOCATION));
        cors.setMaxAge(PREFLIGHT_MAX_AGE);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PolicyPilotProperties properties,
            SessionCookies cookies, RateLimits rateLimits, ErrorResponses errors, SecurityEvents events, Clock clock)
            throws Exception {
        RequestMatcher publicRoutes = publicRoutes();
        AccessCodeFilter sessionFilter = new AccessCodeFilter(cookies, clock, events, publicRoutes);
        CorsFilter cors = new CorsFilter(corsConfigurationSource(properties.web().allowedOrigins()));
        cors.setCorsProcessor(new EnvelopeCorsProcessor(errors, events));
        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicRoutes).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) -> errors.write(response, ErrorCode.SESSION_INVALID))
                        .accessDeniedHandler((request, response, e) -> errors.write(response, ErrorCode.CSRF_REJECTED)))
                .addFilterAfter(cors, HeaderWriterFilter.class)
                .addFilterAfter(new CsrfDefenseFilter(properties.web().allowedOrigins(), errors, events), CorsFilter.class)
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimits, errors, events), AccessCodeFilter.class);
        return http.build();
    }
}

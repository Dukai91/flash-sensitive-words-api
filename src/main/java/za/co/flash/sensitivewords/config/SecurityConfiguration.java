package za.co.flash.sensitivewords.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    @Profile("!production")
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
    }

    @Bean
    @Profile("production")
    SecurityFilterChain productionSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        org.springframework.security.web.AuthenticationEntryPoint unauthorized =
                (request, response, exception) -> problem(json, response, 401, "Unauthorized", "A valid service access token is required");
        org.springframework.security.web.access.AccessDeniedHandler forbidden =
                (request, response, exception) -> problem(json, response, 403, "Forbidden", "The token does not grant the required scope");
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sanitize").hasAuthority("SCOPE_sanitize")
                        .requestMatchers(HttpMethod.GET, "/api/v1/internal/sensitive-words", "/api/v1/internal/sensitive-words/**")
                            .hasAuthority("SCOPE_words:read")
                        .requestMatchers("/api/v1/internal/sensitive-words", "/api/v1/internal/sensitive-words/**")
                            .hasAuthority("SCOPE_words:write")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)).build();
    }

    private static void problem(ObjectMapper json, jakarta.servlet.http.HttpServletResponse response,
                                int status, String title, String detail) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", title, "status", status, "detail", detail));
    }
}

package ucr.ac.cr.medivault.security;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter
        .OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitingFilter
        extends OncePerRequestFilter {

    // Login: 10 peticiones por minuto
    private final Bucket authBucket = Bucket.builder()
            .addLimit(Bandwidth.classic(10,
                Refill.intervally(10,
                    Duration.ofMinutes(1))))
            .build();

    // Expedientes: 30 peticiones por minuto
    private final Bucket apiBucket = Bucket.builder()
            .addLimit(Bandwidth.classic(30,
                Refill.intervally(30,
                    Duration.ofMinutes(1))))
            .build();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Bucket target = null;

        if (path.startsWith("/api/v1/auth/login")) {
            target = authBucket;
        } else if (path.startsWith(
                "/api/v1/expedientes")) {
            target = apiBucket;
        }

        if (target != null) {
            if (target.tryConsume(1)) {
                filterChain.doFilter(
                    request, response);
            } else {
                // Rate Limit superado: HTTP 429
                response.setStatus(
                    HttpStatus
                        .TOO_MANY_REQUESTS.value()
                );
                response.setContentType(
                    MediaType
                        .APPLICATION_PROBLEM_JSON_VALUE
                );
                response.setHeader(
                    "Retry-After", "60");

                String json = """
                    {
                      "type": "%s",
                      "title": "%s",
                      "status": 429,
                      "detail": "%s",
                      "instance": "%s"
                    }
                    """.formatted(
                        "https://medivault.cr/rl",
                        "Demasiadas Peticiones",
                        "Limite superado. Espere 60s.",
                        path
                    );

                response.getWriter().write(json);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

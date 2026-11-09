package ucr.ac.cr.medivault.security;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter
        .OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter
        extends OncePerRequestFilter {

    // Un bucket de login por cada IP
    private final Map<String, Bucket> loginBuckets =
            new ConcurrentHashMap<>();

    // Expedientes: 30 peticiones por minuto
    private final Bucket apiBucket = Bucket.builder()
            .addLimit(Bandwidth.classic(30,
                Refill.intervally(30,
                    Duration.ofMinutes(1))))
            .build();

    // Login: 5 intentos por minuto por IP
    private Bucket nuevoBucketLogin() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5,
                    Refill.intervally(5,
                        Duration.ofMinutes(1))))
                .build();
    }

    public static String obtenerIp(
            HttpServletRequest request) {
        String xff = request.getHeader(
                "X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Bucket target = null;

        if (path.startsWith("/api/v1/auth/login")) {
            target = loginBuckets.computeIfAbsent(
                obtenerIp(request),
                ip -> nuevoBucketLogin());
        } else if (path.startsWith(
                "/api/v1/expedientes")) {
            target = apiBucket;
        }

        if (target != null) {
            if (target.tryConsume(1)) {
                filterChain.doFilter(
                    request, response);
            } else {
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

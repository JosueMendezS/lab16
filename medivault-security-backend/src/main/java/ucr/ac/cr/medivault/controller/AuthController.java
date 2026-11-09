package ucr.ac.cr.medivault.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication
        .BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import ucr.ac.cr.medivault.business.AuthService;
import ucr.ac.cr.medivault.business
        .SecurityAuditorService;
import ucr.ac.cr.medivault.controller.dto.*;
import ucr.ac.cr.medivault.security.RateLimitingFilter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityAuditorService auditor;

    public AuthController(AuthService authService,
            SecurityAuditorService auditor) {
        this.authService = authService;
        this.auditor = auditor;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto req,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                authService.autenticar(req)
            );
        } catch (BadCredentialsException e) {
            auditor.registrar(
                req.username(),
                RateLimitingFilter.obtenerIp(request),
                "LOGIN_FAILED",
                request.getRequestURI(),
                401,
                e.getMessage());
            throw e;
        }
    }
}

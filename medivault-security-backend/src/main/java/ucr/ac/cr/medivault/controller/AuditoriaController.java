package ucr.ac.cr.medivault.controller;

import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;
import ucr.ac.cr.medivault.business
        .SecurityAuditorService;
import ucr.ac.cr.medivault.domain.AuditLog;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    private final SecurityAuditorService auditor;

    public AuditoriaController(
            SecurityAuditorService auditor) {
        this.auditor = auditor;
    }

    // Solo el admin puede ver la bitacora
    @GetMapping
    public ResponseEntity<List<AuditLog>> listar(
            Authentication auth) {
        boolean esAdmin = auth.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority()
                    .equals("ROLE_ADMIN"));
        if (!esAdmin) {
            return ResponseEntity.status(
                HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(auditor.listar());
    }
}

package ucr.ac.cr.medivault.business;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ucr.ac.cr.medivault.data.AuditLogRepository;
import ucr.ac.cr.medivault.domain.AuditLog;

import java.util.List;

@Service
public class SecurityAuditorService {

    private final AuditLogRepository auditRepo;

    public SecurityAuditorService(
            AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    // @Async: se guarda en otro hilo para no frenar la respuesta
    @Async
    public void registrar(String username, String ip,
                          String accion, String recurso,
                          int codigo, String detalle) {
        auditRepo.save(new AuditLog(
            username, ip, accion,
            recurso, codigo, detalle));
    }

    public List<AuditLog> listar() {
        return auditRepo.findAll();
    }
}

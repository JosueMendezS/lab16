package ucr.ac.cr.medivault.business;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ucr.ac.cr.medivault.controller.dto.ExpedienteRequestDto;
import ucr.ac.cr.medivault.controller.dto.ExpedienteResponseDto;
import ucr.ac.cr.medivault.data.ExpedienteMedicoRepository;
import ucr.ac.cr.medivault.data.UsuarioRepository;
import ucr.ac.cr.medivault.domain.ExpedienteMedico;
import ucr.ac.cr.medivault.domain.Rol;
import ucr.ac.cr.medivault.domain.Usuario;
import ucr.ac.cr.medivault.exception.AccessDeniedSecurityException;
import ucr.ac.cr.medivault.exception.ResourceNotFoundException;

@Service
public class ExpedienteService {

    private final ExpedienteMedicoRepository expRepo;
    private final UsuarioRepository userRepo;

    public ExpedienteService(
            ExpedienteMedicoRepository expRepo,
            UsuarioRepository userRepo) {
        this.expRepo = expRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public ExpedienteResponseDto consultarPorId(
            Long expedienteId,
            String currentUsername) {
        ExpedienteMedico exp = expRepo
                .findByIdWithDetails(expedienteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Expediente no existe: " +
                                expedienteId));

        Usuario solicitante = userRepo
                .findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedSecurityException(
                        "Usuario no reconocido."));

        // Verificacion de Propiedad (IDOR / BOLA)
        validarPropiedadRecurso(exp, solicitante);

        return mapToResponseDto(exp);
    }

    @Transactional(readOnly = true)
    public List<ExpedienteResponseDto> listarExpedientesPropios(
            String currentUsername) {
        Usuario solicitante = userRepo
                .findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedSecurityException(
                        "Usuario no reconocido."));

        return expRepo
                .findByPacienteId(solicitante.getId())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional
    public ExpedienteResponseDto registrarExpediente(
            ExpedienteRequestDto dto,
            String doctorUsername) {
        Usuario doc = userRepo
                .findByUsername(doctorUsername)
                .orElseThrow(() -> new AccessDeniedSecurityException(
                        "Doctor no registrado."));

        if (doc.getRol() != Rol.ROLE_DOCTOR &&
                doc.getRol() != Rol.ROLE_ADMIN) {
            throw new AccessDeniedSecurityException(
                    "Solo medicos autorizados " +
                            "pueden registrar expedientes.");
        }

        Usuario pac = userRepo
                .findById(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paciente no existe: " +
                                dto.pacienteId()));

        ExpedienteMedico e = new ExpedienteMedico();
        e.setPaciente(pac);
        e.setMedicoAsignado(doc);
        e.setCodigoExpediente(dto.codigoExpediente());
        e.setDiagnosticoPrincipal(
                dto.diagnosticoPrincipal());
        e.setNotasClinicas(dto.notasClinicas());
        e.setNivelConfidencialidad(
                dto.nivelConfidencialidad());

        ExpedienteMedico guardado = expRepo.save(e);
        return mapToResponseDto(guardado);
    }

    /**
     * Parte 2: Solucion refactorizada y segura para
     * actualizar notas clinicas (reemplaza el metodo
     * vulnerable actualizarNotasVulnerable).
     */
    @Transactional
    public void actualizarNotasDefensivo(
            Long expedienteId,
            String nuevasNotas,
            String currentDoctorUsername) {
        ExpedienteMedico exp = expRepo
                .findByIdWithDetails(expedienteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Expediente no encontrado"));

        Usuario doc = userRepo
                .findByUsername(currentDoctorUsername)
                .orElseThrow(() -> new AccessDeniedSecurityException(
                        "Doctor no valido"));

        if (doc.getRol() != Rol.ROLE_DOCTOR ||
                !exp.getMedicoAsignado().getId()
                        .equals(doc.getId())) {
            throw new AccessDeniedSecurityException(
                    "Solo el medico asignado puede " +
                            "actualizar las notas.");
        }

        if (nuevasNotas == null ||
                nuevasNotas.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "Notas: minimo 10 caracteres.");
        }

        exp.setNotasClinicas(nuevasNotas.trim());
        expRepo.save(exp);
    }

    /**
     * Regla Defensiva de Control de Acceso
     */
    private void validarPropiedadRecurso(
            ExpedienteMedico exp,
            Usuario solicitante) {
        // Regla A: Administrador auditor
        if (solicitante.getRol() == Rol.ROLE_ADMIN) {
            return;
        }

        // Regla B: Paciente dueno del expediente
        if (solicitante.getRol() == Rol.ROLE_PACIENTE) {
            if (!exp.getPaciente().getId()
                    .equals(solicitante.getId())) {
                throw new AccessDeniedSecurityException(
                        "Violacion IDOR: Acceso no autorizado.");
            }
            return;
        }

        // Regla C: Medico asignado al expediente
        if (solicitante.getRol() == Rol.ROLE_DOCTOR) {
            if (!exp.getMedicoAsignado().getId()
                    .equals(solicitante.getId())) {
                throw new AccessDeniedSecurityException(
                        "Violacion BOLA: No asignado.");
            }
        }
    }

    private ExpedienteResponseDto mapToResponseDto(
            ExpedienteMedico e) {
        return new ExpedienteResponseDto(
                e.getId(),
                e.getCodigoExpediente(),
                e.getPaciente().getNombreCompleto(),
                e.getMedicoAsignado()
                        .getNombreCompleto(),
                e.getDiagnosticoPrincipal(),
                e.getNotasClinicas(),
                e.getNivelConfidencialidad(),
                e.getFechaRegistro());
    }
}

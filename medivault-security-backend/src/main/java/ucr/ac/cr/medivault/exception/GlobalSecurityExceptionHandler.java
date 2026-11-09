package ucr.ac.cr.medivault.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedSecurityException.class)
    public ProblemDetail handleAccessDenied(
            AccessDeniedSecurityException ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.FORBIDDEN,
                        ex.getMessage());
        p.setTitle("Acceso Prohibido (IDOR / BOLA)");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/forbidden"));
        return p;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(
            ResourceNotFoundException ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage());
        p.setTitle("Recurso No Encontrado");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/not-found"));
        return p;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(
            BadCredentialsException ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED,
                        "Credenciales invalidas.");
        p.setTitle("Fallo de Autenticacion");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/unauthorized"));
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Validacion de datos fallida.");
        p.setTitle("Error de Validacion");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/bad-request"));

        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult()
                .getFieldErrors()) {
            errors.put(fe.getField(),
                    fe.getDefaultMessage());
        }
        p.setProperty("invalid_fields", errors);
        return p;
    }

    // Parte 2: validacion manual de notas en
    // actualizarNotasDefensivo -> 400 (no 500)
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(
            IllegalArgumentException ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage());
        p.setTitle("Error de Validacion");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/bad-request"));
        return p;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail p = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error interno del servidor.");
        p.setTitle("Error Interno");
        p.setType(URI.create(
                "https://medivault.ucr.ac.cr/internal"));
        return p;
    }
}

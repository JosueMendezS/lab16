# Laboratorio 16 – MediVault Web (IF0009 Desarrollo de Software IV)

Hardening defensivo OWASP en Spring Boot 3.3.4 + Spring Security 6.
Incluye **Parte 1 (práctica guiada)** y **Parte 2 (depuración y análisis)**.
La Parte 3 (reto autónomo) queda pendiente.

## Requisitos
- JDK 21 (el enunciado permite 25 o 21 LTS; Spring Boot 3.3.4 no soporta 25 oficialmente)
- Maven 3.9+ (o el `mvnw` que genera Spring Initializr)
- VS Code con *Extension Pack for Java* y *Spring Boot Extension Pack*

## Cómo abrirlo en VS Code
**Opción A – Abrir directo:** descomprimir y `File > Open Folder…` sobre
`medivault-security-backend`. Ejecutar con el *Spring Boot Dashboard* o con
`mvn spring-boot:run`.

**Opción B – Generarlo con Spring Initializr (como de costumbre):**
1. `Ctrl+Shift+P` → *Spring Initializr: Create a Maven Project*.
2. Group `ucr.ac.cr`, Artifact `medivault-security-backend`, Java 21,
   dependencias: Spring Web, Spring Security, Spring Data JPA, Validation, H2.
3. Reemplazar el `pom.xml` generado por el de este proyecto (agrega JJWT,
   Bucket4j y fija Spring Boot 3.3.4).
4. Borrar la carpeta `src/main` generada y copiar la de este proyecto.
   Borrar también el `src/test` generado (o ajustar su paquete).
5. Ejecutar con `./mvnw spring-boot:run` (o el Dashboard).

## Estructura
```
src/main/java/ucr/ac/cr/medivault/
├── domain/      Usuario, Rol, ExpedienteMedico
├── data/        UsuarioRepository, ExpedienteMedicoRepository
├── business/    AuthService, ExpedienteService (+ actualizarNotasDefensivo)
├── controller/  AuthController, ExpedienteController (+ PATCH /{id}/notas)
│   └── dto/     LoginRequestDto, LoginResponseDto, ExpedienteRequestDto, ExpedienteResponseDto
├── security/    SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, RateLimitingFilter
├── exception/   AccessDeniedSecurityException, ResourceNotFoundException, GlobalSecurityExceptionHandler
└── MedivaultApplication.java
src/main/resources/  application.yml, schema.sql, data.sql
```

## Usuarios de prueba (contraseña de todos: `Password123!`)
| Usuario | Rol | Expedientes |
|---|---|---|
| admin_root | ROLE_ADMIN | ve todos |
| doctor_smith | ROLE_DOCTOR | médico de 101 y 103 |
| doctora_vargas | ROLE_DOCTOR | médica de 102 |
| paciente_carlos | ROLE_PACIENTE | dueño de 101 |
| paciente_maria | ROLE_PACIENTE | dueña de 102 |
| paciente_ana | ROLE_PACIENTE | dueña de 103 |

## Paso 10 – Pruebas
Con la app corriendo, abrir una terminal **Git Bash** en VS Code y ejecutar:
```bash
./pruebas.sh
```
Hace las 5 pruebas del enunciado y toma los tokens JWT automáticamente.
Resultados esperados:
1. Login de Carlos y María → `200` con el token JWT.
2. Cabeceras → `Content-Security-Policy`, `Strict-Transport-Security`,
   `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-cache, no-store…`
3. Carlos → expediente 101 → `200` con los datos clínicos.
4. María → expediente 101 → `403` con ProblemDetail
   `"title": "Acceso Prohibido (IDOR / BOLA)"`.
5. 12 logins fallidos seguidos → `401` y luego `429` (con `Retry-After: 60`).

## Ajustes respecto al enunciado (mínimos, para que funcione y cumpla la rúbrica)
- **Java 21** en lugar de 25 (ver Requisitos).
- **application.yml:** la URL de H2 y el `secret` van en una sola línea; con
  el bloque `>` del enunciado YAML les agrega espacios.
- **SecurityConfig:** `contentTypeOptions` y `cacheControl` usan
  `Customizer.withDefaults()`. Con `::disable` (como venía) se **quitan**
  las cabeceras nosniff y no-cache, al revés de lo que dicen los comentarios
  y la rúbrica.
- **HSTS:** se agregó `.requestMatcher(AnyRequestMatcher.INSTANCE)`. Por
  defecto Spring solo la envía por HTTPS, así que en `http://localhost`
  no aparecería en la Prueba 2.
- **Excepciones:** se usan los nombres del código (`AccessDeniedSecurityException`,
  `GlobalSecurityExceptionHandler`); el árbol de carpetas del enunciado decía
  `AccessDeniedException` / `GlobalExceptionHandler`.
- **Mensaje IDOR:** `"Violacion IDOR: Acceso no autorizado."`, igual a la
  salida esperada de la Prueba 4.
- **Prueba 2:** `curl -I` envía HEAD a un endpoint que solo acepta POST y
  devolvería 403. El script hace un POST con `-i` para ver las cabeceras.
- **Prueba 5:** con `"password": "err"` la validación (mínimo 8 caracteres)
  responde `400`, no `401`. El script usa `ClaveErronea1` para obtener los 401
  esperados.
- **Parte 2:** se agregó un `@ExceptionHandler(IllegalArgumentException)` → 400
  para que la validación de notas no termine en 500.

## Parte 2 – Análisis forense del método vulnerable
**1. Brecha identificada.** `actualizarNotasVulnerable`:
- No recibe `Authentication`: no sabe quién hace la petición.
- No verifica rol ni que el usuario sea el `medicoAsignado` del expediente:
  es un **Broken Object Level Authorization (BOLA/IDOR)**, porque basta cambiar
  el `{id}` de la URL.
- Acepta cualquier `String` sin validar longitud ni contenido (entrada no confiable).
- `findById(id).orElseThrow()` lanza `NoSuchElementException`, que sin un
  manejador termina en un 500 genérico.
- No deja traza de auditoría.

**2. Explotación.** Cualquier usuario autenticado (incluso un paciente) puede
ejecutar `PATCH /api/v1/expedientes/{id}/notas` sobre el expediente de otra
persona y reescribir sus notas clínicas. Eso compromete la integridad del
registro médico.

**3. Solución aplicada.** `ExpedienteService.actualizarNotasDefensivo(...)`:
- Busca el expediente con `findByIdWithDetails`; si no existe → `ResourceNotFoundException` (404).
- Identifica al usuario autenticado (`auth.getName()` en el controlador).
- Exige `ROLE_DOCTOR` **y** que sea `medicoAsignado` → si no, `AccessDeniedSecurityException` (403 RFC 7807).
- Valida que las notas no vengan vacías y tengan al menos 10 caracteres → 400.
- Hace `trim()` y guarda dentro de una transacción.

Endpoint seguro: `PATCH /api/v1/expedientes/{id}/notas` (texto plano en el cuerpo).

Prueba rápida (Git Bash):
```bash
# token de doctor_smith (asignado al 101) -> 204
# token de doctora_vargas (no asignada)   -> 403
# token de paciente_carlos (no es doctor) -> 403
curl -i -X PATCH http://localhost:8080/api/v1/expedientes/101/notas \
  -H "Authorization: Bearer <TOKEN>" -H "Content-Type: text/plain" \
  -d "Control de presion estable, continuar tratamiento."
```

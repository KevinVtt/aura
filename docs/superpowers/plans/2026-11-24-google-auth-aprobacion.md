# Login con Google y aprobación de usuarios — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el login con contraseña por "Continuar con Google", con solicitud de alta y aprobación desde el panel de administración.

**Architecture:** El frontend obtiene un ID token de Google Identity Services y lo manda al backend. El backend lo valida con `GoogleIdTokenVerifier` (firma, `aud`, expiración, `email_verified`) y responde con el estado del usuario: si está aprobado emite el JWT propio de siempre; si no, indica `NO_REGISTRADO`, `PENDIENTE`, `RECHAZADO` o `INACTIVO`. El alta crea el usuario en estado `PENDIENTE`; el admin lo aprueba y se envía el mail. El único login con contraseña que queda es el del super admin de `aura.admin.email`.

**Tech Stack:** Spring Boot 3.5.11 / Java 21 / Maven, `com.google.api-client:google-api-client:2.7.0`, PostgreSQL, JUnit 5 + MockMvc; React 18 (CRA) con Google Identity Services vía script.

**Spec:** Conversación del 2026-11-24 (aprobada por el usuario). No hay archivo de spec separado.

## Global Constraints

- Gratis: no agregar ningún servicio pago ni cuenta de facturación de Google.
- Java 21, Spring Boot 3.5.11, sin cambiar de framework ni de lenguaje.
- Código en inglés, mensajes al usuario en español, commits convencionales (`feat:`, `fix:`).
- El JWT propio, `JwtFilter`, roles y `SecurityConfig` stateless no cambian de forma.
- `Usuario.estado == null` (filas viejas) se trata como `APROBADO`.
- Nunca versionar `.env` ni `application.properties`.
- Comandos: backend `cd backend/project-aura; .\mvnw.cmd test`; IA sin cambios; frontend `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false`.

## Review Focus

- Token de Google emitido para otra app (`aud` distinto) → 401, nunca JWT propio.
- Email existente en estado `PENDIENTE` → sin JWT, sin duplicar la solicitud.
- Nombre que devuelve Google vacío o de más de 100 caracteres → no debe romper el alta.
- Script de Google bloqueado, lento u offline → la pantalla de login no queda vacía.
- Filas viejas con `estado = null` → deben poder iniciar sesión igual.
- Usuario `RECHAZADO` que vuelve a pedir acceso → mensaje claro, sin 500.

---

### Task 0: Rama de trabajo

- [ ] **Step 1: Crear la rama**

```powershell
git checkout -b feat/google-auth-aprobacion
```

- [ ] **Step 2: Verificar que el árbol está limpio**

Run: `git status --short`
Expected: sin archivos modificados (solo este plan, si recién se creó).

---

### Task 1: Dominio y alta por aprobación

**Files:**
- Create: `backend/project-aura/src/main/java/com/project/aura/models/enums/EstadoUsuario.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/models/Usuario.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/repositories/UsuarioRepository.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/services/UsuarioService.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/services/MailService.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/controllers/AuthController.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/controllers/UsuarioController.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/dto/LoginResponse.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/config/DataInitializer.java`
- Delete: `backend/project-aura/src/main/java/com/project/aura/dto/CambioPasswordRequest.java`
- Test: `backend/project-aura/src/test/java/com/project/aura/services/UsuarioServiceTest.java`
- Test: `backend/project-aura/src/test/java/com/project/aura/controllers/UsuarioControllerTest.java`

**Interfaces:**
- Produces: `enum EstadoUsuario { PENDIENTE, APROBADO, RECHAZADO }`; `Usuario.getEstado()/setEstado(EstadoUsuario)`; `UsuarioService.registrarSolicitud(String nombre, String email, String telefono) : Usuario`; `UsuarioService.aprobar(Long id) : Usuario`; `UsuarioService.rechazar(Long id) : Usuario`; `UsuarioService.listarPendientes() : List<Usuario>`; `MailService.enviarAprobado(String destino, String nombre) : void`; `UsuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario) : List<Usuario>`.

- [ ] **Step 1: Escribir los tests que fallan**

En `UsuarioServiceTest`, borrar `crear_guardaUsuarioConPasswordEncodeadoYActivo`, `crear_sinPassword_generaUnaYObligaACambiarla`, `cambiarPassword_conActualIncorrecta_noCambia` y agregar:

```java
@Test
void crear_guardaAprobadoSinPasswordYAvisaPorCorreo() {
    when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.empty());
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    Usuario resultado = usuarioService.crear(usuario);

    assertNull(resultado.getPassword());
    assertEquals(EstadoUsuario.APROBADO, resultado.getEstado());
    assertTrue(resultado.getActivo());
    verify(mailService).enviarAprobado("juan@aura.com", "Juan");
}

@Test
void registrarSolicitud_creaPendienteInactivo() {
    when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.empty());
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    Usuario resultado = usuarioService.registrarSolicitud("Juan", "juan@aura.com", "1122334455");

    assertEquals(EstadoUsuario.PENDIENTE, resultado.getEstado());
    assertFalse(resultado.getActivo());
    assertEquals(Rol.EMPLEADO, resultado.getRol());
    assertEquals("1122334455", resultado.getContacto().getTelefono());
    verify(mailService, never()).enviarAprobado(any(), any());
}

@Test
void registrarSolicitud_yaPendiente_devuelveLaMisma() {
    usuario.setEstado(EstadoUsuario.PENDIENTE);
    when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.of(usuario));

    Usuario resultado = usuarioService.registrarSolicitud("Juan", "juan@aura.com", null);

    assertEquals(1L, resultado.getId());
    verify(usuarioRepository, never()).save(any());
}

@Test
void registrarSolicitud_emailYaAprobado_lanzaConflict() {
    usuario.setEstado(EstadoUsuario.APROBADO);
    when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.of(usuario));

    ApiException ex = assertThrows(ApiException.class,
        () -> usuarioService.registrarSolicitud("Juan", "juan@aura.com", null));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
}

@Test
void registrarSolicitud_nombreDeGoogleDemasiadoLargo_seRecorta() {
    when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.empty());
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    Usuario resultado = usuarioService.registrarSolicitud("x".repeat(300), "juan@aura.com", null);

    assertEquals(100, resultado.getNombre().length());
}

@Test
void aprobar_activaYEnviaElCorreo() {
    usuario.setEstado(EstadoUsuario.PENDIENTE);
    usuario.setActivo(false);
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    Usuario resultado = usuarioService.aprobar(1L);

    assertEquals(EstadoUsuario.APROBADO, resultado.getEstado());
    assertTrue(resultado.getActivo());
    verify(mailService).enviarAprobado("juan@aura.com", "Juan");
}

@Test
void rechazar_marcaRechazadoEInactivo() {
    usuario.setEstado(EstadoUsuario.PENDIENTE);
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    Usuario resultado = usuarioService.rechazar(1L);

    assertEquals(EstadoUsuario.RECHAZADO, resultado.getEstado());
    assertFalse(resultado.getActivo());
}

@Test
void listarPendientes_pideSoloPendientes() {
    when(usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.PENDIENTE))
        .thenReturn(List.of(usuario));

    assertEquals(1, usuarioService.listarPendientes().size());
}
```

En `UsuarioControllerTest`: quitar `"password":"password123",` de `bodyValido()`, borrar `crear_passwordCorta_devuelve400`, y agregar:

```java
@Test
@WithMockUser(roles = "ADMIN")
void pendientes_devuelve200() throws Exception {
    when(usuarioService.listarPendientes()).thenReturn(List.of(usuario()));
    mockMvc.perform(get("/usuarios/pendientes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nombre").value("Juan"));
}

@Test
@WithMockUser(roles = "ADMIN")
void aprobar_devuelve204() throws Exception {
    when(usuarioService.aprobar(1L)).thenReturn(usuario());
    mockMvc.perform(patch("/usuarios/1/aprobar")).andExpect(status().isNoContent());
    verify(usuarioService).aprobar(1L);
}

@Test
@WithMockUser(roles = "ADMIN")
void rechazar_devuelve204() throws Exception {
    when(usuarioService.rechazar(1L)).thenReturn(usuario());
    mockMvc.perform(patch("/usuarios/1/rechazar")).andExpect(status().isNoContent());
    verify(usuarioService).rechazar(1L);
}

@Test
@WithMockUser(roles = "ADMIN")
void reenviarMail_devuelve204() throws Exception {
    when(usuarioService.reenviarAviso(1L)).thenReturn(usuario());
    mockMvc.perform(post("/usuarios/1/reenviar-mail")).andExpect(status().isNoContent());
}
```

- [ ] **Step 2: Correr los tests y ver que fallan**

Run: `cd backend/project-aura; .\mvnw.cmd test -Dtest=UsuarioServiceTest,UsuarioControllerTest`
Expected: FAIL de compilación (`getEstado`, `registrarSolicitud`, `enviarAprobado` no existen).

- [ ] **Step 3: Implementar el dominio**

`EstadoUsuario`:

```java
package com.project.aura.models.enums;

public enum EstadoUsuario { PENDIENTE, APROBADO, RECHAZADO }
```

En `Usuario`: borrar el campo `mustChangePassword` y su comentario; en `password` borrar `@Size(min = 8)` y `nullable = false` (el super admin lo usa; los usuarios de Google quedan en null). Agregar:

```java
// null = fila previa a la aprobación: se trata como APROBADO al iniciar sesión.
@Enumerated(EnumType.STRING)
@Column(name = "estado")
private EstadoUsuario estado;
```

En `UsuarioRepository`: `List<Usuario> findByEstadoOrderByNombreAsc(EstadoUsuario estado);`

En `MailService`: borrar `enviarAlta` y agregar:

```java
public void enviarAprobado(String destino, String nombre) {
    SimpleMailMessage mensaje = new SimpleMailMessage();
    mensaje.setFrom("Aura <" + from + ">");
    mensaje.setTo(destino);
    mensaje.setSubject("Tu acceso a Aura fue aprobado");
    mensaje.setText(
        "Hola " + nombre + ",\n\n" +
        "Tu acceso a Aura ya está aprobado.\n\n" +
        "Entrá en " + loginUrl + " con «Continuar con Google» usando esta misma cuenta: " + destino + "\n");
    try {
        mailSender.send(mensaje);
    } catch (MailException e) {
        throw new ApiException(HttpStatus.BAD_GATEWAY,
            "No se pudo enviar el correo. Usá «Reenviar aviso» para intentar de nuevo.");
    }
}
```

En `UsuarioService`: borrar `cambiarPassword`, `generarPassword`, `CONJUNTOS`, `RANDOM`, el campo `passwordEncoder` y sus imports. `crear` queda sin generar clave ni codificar: normaliza email, valida dominio, 409 si existe, `setPassword(null)`, `setActivo(true)`, `setEstado(APROBADO)`, guarda y `despuesDelCommit(() -> mailService.enviarAprobado(email, nombre))`. Renombrar `reenviarAlta` a `reenviarAviso` (mismo patrón, sin clave, con `enviarAprobado`). Agregar los cuatro métodos nuevos; `registrarSolicitud` reusa la normalización y la validación de dominio ya existentes y recorta el nombre a 100 caracteres (`nombre.length() > 100 ? nombre.substring(0, 100) : nombre`, y si viene vacío usa el email).

En `AuthController`: borrar el método `cambiarPassword` y el import. En `LoginResponse`: borrar `mustChangePassword`. Borrar `CambioPasswordRequest.java`. En `DataInitializer`: agregar `.estado(EstadoUsuario.APROBADO)` al builder.

En `UsuarioController`: `reenviarAlta` pasa a `reenviarAviso` en `POST /{id}/reenviar-mail`; agregar `GET /pendientes`, `PATCH /{id}/aprobar`, `PATCH /{id}/rechazar` con `@PreAuthorize("hasRole('ADMIN')")` ya presente a nivel de clase.

- [ ] **Step 4: Correr los tests y ver que pasan**

Run: `cd backend/project-aura; .\mvnw.cmd test`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add backend/project-aura
git commit -m "feat(backend): alta de usuarios pendiente de aprobación sin contraseña"
```

---

### Task 2: Validar el ID token de Google

**Files:**
- Modify: `backend/project-aura/pom.xml`
- Create: `backend/project-aura/src/main/java/com/project/aura/services/GoogleAuthService.java`
- Modify: `backend/project-aura/src/main/resources/application.properties.example`
- Test: `backend/project-aura/src/test/java/com/project/aura/services/GoogleAuthServiceTest.java`

**Interfaces:**
- Produces: `GoogleAuthService.verificar(String idToken) : GoogleIdToken.Payload` — lanza `ApiException(401)` si el token es inválido o el email no está verificado; `ApiException(503)` si no hay Client ID configurado.

- [ ] **Step 1: Escribir los tests que fallan**

```java
package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.project.aura.exception.ApiException;

class GoogleAuthServiceTest {

    @Test
    void verificar_sinClientId_devuelve503() {
        GoogleAuthService service = new GoogleAuthService();
        ReflectionTestUtils.setField(service, "clientId", "");

        ApiException ex = assertThrows(ApiException.class, () -> service.verificar("cualquiera"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void verificar_tokenIlegible_devuelve401() {
        GoogleAuthService service = new GoogleAuthService();
        ReflectionTestUtils.setField(service, "clientId", "client-id-de-prueba");

        ApiException ex = assertThrows(ApiException.class, () -> service.verificar("no-es-un-jwt"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
```

- [ ] **Step 2: Correr y ver que falla**

Run: `cd backend/project-aura; .\mvnw.cmd test -Dtest=GoogleAuthServiceTest`
Expected: FAIL de compilación (`GoogleAuthService` no existe).

- [ ] **Step 3: Implementar**

En `pom.xml`, dentro de `<dependencies>`:

```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.0</version>
</dependency>
```

`GoogleAuthService` (clase de servicio normal, sin constructor con campos): campo `@Value("${aura.google.client-id:}") private String clientId;`, un `GoogleIdTokenVerifier` cacheado que se construye en el primer uso con `.setAudience(List.of(clientId))`, y `verificar` que lanza 503 si `clientId.isBlank()`, 401 si `verify(...)` devuelve null o lanza `Exception`, y 401 si `!Boolean.TRUE.equals(payload.getEmailVerified())`.

En `application.properties.example`, debajo de la sección del admin:

```
# Login con Google (Google Cloud Console > OAuth client ID). Vacio = login deshabilitado.
aura.google.client-id=${GOOGLE_CLIENT_ID:}
```

- [ ] **Step 4: Correr y ver que pasa**

Run: `cd backend/project-aura; .\mvnw.cmd test -Dtest=GoogleAuthServiceTest`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```powershell
git add backend/project-aura
git commit -m "feat(backend): validar el ID token de Google"
```

---

### Task 3: Endpoints `/auth/google` y `/auth/solicitud`

**Files:**
- Create: `backend/project-aura/src/main/java/com/project/aura/dto/GoogleLoginRequest.java`
- Create: `backend/project-aura/src/main/java/com/project/aura/dto/SolicitudRequest.java`
- Create: `backend/project-aura/src/main/java/com/project/aura/dto/GoogleLoginResponse.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/controllers/AuthController.java`
- Modify: `backend/project-aura/src/main/java/com/project/aura/config/SecurityConfig.java`
- Test: `backend/project-aura/src/test/java/com/project/aura/controllers/AuthControllerTest.java`

**Interfaces:**
- Consumes: `GoogleAuthService.verificar(String) : Payload`, `UsuarioService.registrarSolicitud(...)`.
- Produces: `POST /auth/google` con `{"idToken": "..."}` → 200 `GoogleLoginResponse{token, nombre, email, rol, estado}`; `POST /auth/solicitud` con `{"idToken": "...", "telefono": "..."}` → 201.

- [ ] **Step 1: Escribir los tests que fallan**

En `AuthControllerTest`: agregar `@MockBean private GoogleAuthService googleAuthService;` y estos helpers, más los tests:

```java
private GoogleIdToken.Payload payload(String email, String nombre) {
    GoogleIdToken.Payload p = new GoogleIdToken.Payload();
    p.setEmail(email);
    p.setEmailVerified(true);
    p.set("name", nombre);
    return p;
}

// estado null = fila anterior a la aprobación.
private Usuario conEstado(String email, EstadoUsuario estado) {
    Usuario u = usuarioAdmin();
    u.getContacto().setEmail(email);
    u.setEstado(estado);
    u.setRol(Rol.EMPLEADO);
    return u;
}

private Usuario aprobado(String email) { return conEstado(email, null); }
```


```java
@Test
void google_aprobado_devuelveToken() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("ana@gmail.com", "Ana"));
    when(usuarioRepository.findByContactoEmail("ana@gmail.com"))
        .thenReturn(Optional.of(aprobado("ana@gmail.com")));
    when(jwtUtil.generarToken("ana@gmail.com", "EMPLEADO")).thenReturn("token-jwt");

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APROBADO"))
        .andExpect(jsonPath("$.token").value("token-jwt"));
}

@Test
void google_pendiente_noDevuelveToken() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("ana@gmail.com", "Ana"));
    when(usuarioRepository.findByContactoEmail("ana@gmail.com"))
        .thenReturn(Optional.of(conEstado("ana@gmail.com", EstadoUsuario.PENDIENTE)));

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.token").doesNotExist());

    verify(jwtUtil, never()).generarToken(any(), any());
}

@Test
void google_rechazado_noDevuelveToken() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("ana@gmail.com", "Ana"));
    when(usuarioRepository.findByContactoEmail("ana@gmail.com"))
        .thenReturn(Optional.of(conEstado("ana@gmail.com", EstadoUsuario.RECHAZADO)));

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECHAZADO"))
        .andExpect(jsonPath("$.token").doesNotExist());

    verify(jwtUtil, never()).generarToken(any(), any());
}

@Test
void google_noRegistrado_devuelveNoRegistrado() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("ana@gmail.com", "Ana"));
    when(usuarioRepository.findByContactoEmail("ana@gmail.com")).thenReturn(Optional.empty());

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("NO_REGISTRADO"))
        .andExpect(jsonPath("$.email").value("ana@gmail.com"));
}

@Test
void google_estadoNulo_entraComoAprobado() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("viejo@aura.com", "Viejo"));
    when(usuarioRepository.findByContactoEmail("viejo@aura.com"))
        .thenReturn(Optional.of(aprobado("viejo@aura.com")));
    when(jwtUtil.generarToken("viejo@aura.com", "EMPLEADO")).thenReturn("token-jwt");

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APROBADO"));
}

@Test
void google_tokenInvalido_devuelve401() throws Exception {
    when(googleAuthService.verificar("malo"))
        .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Token de Google inválido"));

    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"malo\"}"))
        .andExpect(status().isUnauthorized());
}

@Test
void google_sinIdToken_devuelve400() throws Exception {
    mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.idToken").exists());
}

@Test
void solicitud_creaPendiente_devuelve201() throws Exception {
    when(googleAuthService.verificar("id-token")).thenReturn(payload("ana@gmail.com", "Ana"));

    mockMvc.perform(post("/auth/solicitud").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"id-token\",\"telefono\":\"1122334455\"}"))
        .andExpect(status().isCreated());

    verify(usuarioService).registrarSolicitud("Ana", "ana@gmail.com", "1122334455");
}

@Test
void login_conEmailQueNoEsElSuperAdmin_devuelve403() throws Exception {
    mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ana@gmail.com\",\"password\":\"password123\"}"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Correr y ver que falla**

Run: `cd backend/project-aura; .\mvnw.cmd test -Dtest=AuthControllerTest`
Expected: FAIL (endpoints 404 / 403 no implementado).

- [ ] **Step 3: Implementar**

DTOs con Lombok: `GoogleLoginRequest` (`@NotBlank String idToken`), `SolicitudRequest` (`@NotBlank String idToken`, `String telefono` con el mismo `@Pattern(regexp = "^$|^[0-9]{10,15}$")`), `GoogleLoginResponse{String token, String nombre, String email, String rol, String estado}` con `@Builder`.

En `AuthController`: inyectar `GoogleAuthService` y `@Value("${aura.admin.email:admin@aura.com}") private String adminEmail;`. En `login`, antes del rate limiter:

```java
if (!email.equalsIgnoreCase(adminEmail)) {
    throw new ApiException(HttpStatus.FORBIDDEN,
        "Ese acceso ya no usa contraseña: entrá con «Continuar con Google».");
}
```

`google(...)`: verifica el token, normaliza el email, busca el usuario y arma la respuesta en este orden — sin usuario → `NO_REGISTRADO`; `estado == RECHAZADO` → `RECHAZADO`; `estado == PENDIENTE` → `PENDIENTE`; `!activo` → `INACTIVO`; si no, `APROBADO` + JWT. `estado == null` cae en el último caso.

`solicitud(...)`: verifica el token y llama a `usuarioService.registrarSolicitud(payload.get("name"), email, request.getTelefono())`; devuelve 201.

En `SecurityConfig`, junto a `/auth/login`: `.requestMatchers("/auth/google", "/auth/solicitud").permitAll()`.

- [ ] **Step 4: Correr todo el backend**

Run: `cd backend/project-aura; .\mvnw.cmd test`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add backend/project-aura
git commit -m "feat(backend): login con Google y solicitud de acceso"
```

---

### Task 4: Quitar el cambio de contraseña del frontend

**Files:**
- Delete: `frontend/aura-frontend/src/pages/CambiarPassword.jsx`
- Modify: `frontend/aura-frontend/src/App.jsx`
- Modify: `frontend/aura-frontend/src/context/AuthContext.jsx`
- Modify: `frontend/aura-frontend/src/components/ProtectedRoute.jsx`
- Modify: `frontend/aura-frontend/src/components/Navbar.jsx`
- Test: `frontend/aura-frontend/src/context/AuthContext.test.js`

**Interfaces:**
- Produces: el objeto de usuario en `localStorage` queda `{token, nombre, rol}`.

- [ ] **Step 1: Escribir el test que falla**

En `AuthContext.test.js`, agregar:

```javascript
test('login no guarda mustChangePassword', () => {
  const { container, root } = renderProvider();
  act(() => {
    container.querySelector('#btn-login').dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  expect(localStorage.getItem('mustChangePassword')).toBeNull();
  unmount(root);
});
```

- [ ] **Step 2: Correr y ver que falla**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false --testPathPattern=AuthContext`
Expected: FAIL (se guarda `mutChangePassword`).

- [ ] **Step 3: Implementar**

`AuthContext`: quitar `mustChangePassword` del estado inicial, de `login` y del objeto que se setea. `ProtectedRoute`: quitar el `if (user.mustChangePassword)`. `App.jsx`: quitar el import y la ruta `/cambiar-password`. `Navbar.jsx`: quitar el `<Link>` a `/cambiar-password`. Borrar `CambiarPassword.jsx`.

- [ ] **Step 4: Correr los tests del frontend**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false`
Expected: PASS salvo `Login.test.js`, que se arregla en la Task 5.

- [ ] **Step 5: Commit**

```powershell
git add frontend/aura-frontend
git commit -m "refactor(frontend): quitar el cambio de contraseña"
```

---

### Task 5: Pantalla de login con Google

**Files:**
- Modify: `frontend/aura-frontend/public/index.html`
- Modify: `frontend/aura-frontend/src/pages/Login.jsx`
- Test: `frontend/aura-frontend/src/pages/Login.test.js`

**Interfaces:**
- Consumes: `POST /auth/google` → `{estado, token, nombre, email, rol}`; `POST /auth/solicitud` → 201; `POST /auth/login` (solo super admin).

- [ ] **Step 1: Reescribir los tests**

Reemplazar el contenido de `Login.test.js` por tests de estos casos, mockeando GIS con un objeto `window.google` propio (capturar el callback de `initialize` para dispararlo a mano) y `api.post`:

```javascript
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Login from './Login';

jest.mock('../api/axios', () => ({ post: jest.fn() }));
jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));

import api from '../api/axios';
import { useAuth } from '../context/AuthContext';

const mockLogin = jest.fn();
let googleCallback;

function renderLogin() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/admin" element={<div data-testid="admin-page" />} />
          <Route path="/chat" element={<div data-testid="chat-page" />} />
        </Routes>
      </MemoryRouter>
    );
  });
  return { container, root };
}

async function credencial(credential) {
  await act(async () => { await googleCallback({ credential }); });
}

beforeEach(() => {
  process.env.REACT_APP_GOOGLE_CLIENT_ID = 'client-id-de-prueba';
  useAuth.mockReturnValue({ login: mockLogin });
  mockLogin.mockClear();
  api.post.mockReset();
  window.google = {
    accounts: {
      id: {
        initialize: jest.fn(({ callback }) => { googleCallback = callback; }),
        renderButton: jest.fn(),
      },
    },
  };
});

afterEach(() => {
  delete window.google;
  delete process.env.REACT_APP_GOOGLE_CLIENT_ID;
  document.body.innerHTML = '';
});

test('sin REACT_APP_GOOGLE_CLIENT_ID muestra un aviso de configuración', () => {
  delete process.env.REACT_APP_GOOGLE_CLIENT_ID;
  const { container, root } = renderLogin();
  expect(container.textContent).toContain('no está configurado');
  act(() => root.unmount());
});

test('si el script de Google no carga, muestra un aviso en vez de quedar vacío', () => {
  const { container, root } = renderLogin();
  expect(container.querySelector('.auth-error')).not.toBeNull();
  act(() => root.unmount());
});

test('credencial APROBADA inicia sesión y navega según el rol', async () => {
  const data = { estado: 'APROBADO', token: 'tok', nombre: 'Ana', rol: 'EMPLEADO' };
  api.post.mockResolvedValue({ data });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(api.post).toHaveBeenCalledWith('/auth/google', { idToken: 'id-token' });
  expect(mockLogin).toHaveBeenCalledWith(data);
  expect(container.querySelector('[data-testid="chat-page"]')).not.toBeNull();
  act(() => root.unmount());
});

test('estado PENDIENTE muestra el mensaje y no inicia sesión', async () => {
  api.post.mockResolvedValue({ data: { estado: 'PENDIENTE', email: 'ana@gmail.com' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(container.textContent).toContain('pendiente de aprobación');
  expect(mockLogin).not.toHaveBeenCalled();
  act(() => root.unmount());
});

test('estado NO_REGISTRADO pide el teléfono y manda la solicitud', async () => {
  api.post.mockResolvedValueOnce({ data: { estado: 'NO_REGISTRADO', email: 'ana@gmail.com', nombre: 'Ana' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  const tel = container.querySelector('input[name="telefono"]');
  expect(tel).not.toBeNull();
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  act(() => { setter.call(tel, '1122334455'); tel.dispatchEvent(new Event('input', { bubbles: true })); });
  api.post.mockResolvedValueOnce({ data: {} });
  await act(async () => {
    container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  expect(api.post).toHaveBeenLastCalledWith('/auth/solicitud', { idToken: 'id-token', telefono: '1122334455' });
  expect(container.textContent).toContain('pendiente de aprobación');
  act(() => root.unmount());
});

test('estado RECHAZADO muestra el mensaje de rechazo', async () => {
  api.post.mockResolvedValue({ data: { estado: 'RECHAZADO' } });
  const { container, root } = renderLogin();
  await credencial('id-token');
  expect(container.textContent).toContain('no fue aprobado');
  act(() => root.unmount());
});

test('el acceso de administrador manda a /auth/login', async () => {
  api.post.mockResolvedValue({ data: { token: 'tok', nombre: 'Admin', rol: 'ADMIN' } });
  const { container, root } = renderLogin();
  act(() => {
    container.querySelector('.link-admin').dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  const email = container.querySelector('input[name="email"]');
  const pass = container.querySelector('input[name="password"]');
  act(() => {
    setter.call(email, 'admin@aura.com'); email.dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(pass, 'secreto'); pass.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => {
    container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'admin@aura.com', password: 'secreto' });
  expect(container.querySelector('[data-testid="admin-page"]')).not.toBeNull();
  act(() => root.unmount());
});
```

El componente debe mostrar el aviso de "script no disponible" mientras `window.google` no exista (no espera en silencio) y reemplazarlo por el botón cuando aparezca; así el caso de script bloqueado pasa sin timers en el test.

- [ ] **Step 2: Correr y ver que fallan**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false --testPathPattern=Login`
Expected: FAIL (no existe el botón de Google).

- [ ] **Step 3: Implementar**

`public/index.html`: `<script src="https://accounts.google.com/gsi/client" async defer></script>` antes del bundle.

`Login.jsx`: estado local `vista` (`inicio`, `registro`, `pendiente`, `rechazado`, `inactivo`), `perfil` (`{email, nombre}`) y `idToken`. Al montar, si `process.env.REACT_APP_GOOGLE_CLIENT_ID` está vacío → aviso; si no, poll de `window.google` cada 100 ms hasta 8 s, después `google.accounts.id.initialize({client_id, callback: handleCredential})` y `google.accounts.id.renderButton(ref.current, {theme: 'filled_black', size: 'large', text: 'continue_with', shape: 'pill'})`. `handleCredential` hace `POST /auth/google` y cambia de vista según `estado`; en `APROBADO` llama `login(data)` y navega a `/admin` o `/chat`. El formulario de registro pide solo teléfono, guarda el `idToken` recibido y hace `POST /auth/solicitud`. Al final, un `<button className="link-admin">Acceso administrador</button>` que muestra el formulario email/contraseña de hoy (mismo `POST /auth/login`), con el manejo de 403/429/401 ya existente.

Reusar las clases CSS que ya existen (`auth-layout`, `auth-card`, `field`, `btn-primary`, `auth-error`); agregar solo las reglas mínimas para `.google-slot` y `.link-admin` en `Login.css`.

- [ ] **Step 4: Correr y ver que pasan**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false --testPathPattern=Login`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add frontend/aura-frontend
git commit -m "feat(frontend): login con Google y solicitud de acceso"
```

---

### Task 6: Pestaña de solicitudes en el panel

**Files:**
- Modify: `frontend/aura-frontend/src/pages/AdminPanel.jsx`
- Modify: `frontend/aura-frontend/src/pages/AdminPanel.css`
- Test: `frontend/aura-frontend/src/pages/AdminPanel.test.js`

**Interfaces:**
- Consumes: `GET /usuarios/pendientes`, `PATCH /usuarios/{id}/aprobar`, `PATCH /usuarios/{id}/rechazar`, `POST /usuarios/{id}/reenviar-mail`.

- [ ] **Step 1: Escribir los tests**

Cambiar `irAUsuarios` para buscar la pestaña por texto (`[...container.querySelectorAll('.tab')].find((t) => t.textContent.includes('Usuarios'))`) porque ahora hay tres pestañas. Agregar:

```javascript
function irAPestania(container, texto) {
  const tab = [...container.querySelectorAll('.tab')].find((t) => t.textContent.includes(texto));
  act(() => { tab.dispatchEvent(new MouseEvent('click', { bubbles: true })); });
}

test('la pestaña Solicitudes muestra los pendientes y aprueba', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/usuarios/pendientes') {
      return Promise.resolve({ data: [{ id: 7, nombre: 'Ana', contacto: { email: 'ana@gmail.com', telefono: '1122334455' } }] });
    }
    return Promise.resolve({ data: [] });
  });
  api.patch.mockResolvedValue({ data: {} });
  const { container, root } = renderPanel();
  await act(async () => {});
  await act(async () => { irAPestania(container, 'Solicitudes'); });

  expect(container.textContent).toContain('ana@gmail.com');
  const aprobar = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Aprobar');
  await act(async () => { aprobar.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

  expect(api.patch).toHaveBeenCalledWith('/usuarios/7/aprobar');
  unmount(root);
});

test('rechazar manda el pedido al backend', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/usuarios/pendientes') {
      return Promise.resolve({ data: [{ id: 7, nombre: 'Ana', contacto: { email: 'ana@gmail.com' } }] });
    }
    return Promise.resolve({ data: [] });
  });
  api.patch.mockResolvedValue({ data: {} });
  const { container, root } = renderPanel();
  await act(async () => {});
  await act(async () => { irAPestania(container, 'Solicitudes'); });
  const rechazar = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Rechazar');
  await act(async () => { rechazar.dispatchEvent(new MouseEvent('click', { bubbles: true })); });

  expect(api.patch).toHaveBeenCalledWith('/usuarios/7/rechazar');
  unmount(root);
});

test('el formulario de alta ya no pide contraseña', async () => {
  const { container, root } = renderPanel();
  await act(async () => {});
  await act(async () => { irAPestania(container, 'Usuarios'); });
  expect(container.querySelector('input[name="password"]')).toBeNull();
  unmount(root);
});
```

- [ ] **Step 2: Correr y ver que fallan**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false --testPathPattern=AdminPanel`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Agregar `solicitudes` + `cargarSolicitudes()` (llamada en el `useEffect` inicial), la pestaña `Solicitudes` con `<span className="tab-count">{solicitudes.length}</span>`, y su contenido: lista con nombre, email y teléfono, botón `Aprobar` (`PATCH /usuarios/{id}/aprobar`) y `Rechazar` (`PATCH /usuarios/{id}/rechazar`), recargando ambas listas y avisando con el toast existente. Quitar el campo `password` del `userForm` y del formulario; el texto de éxito pasa a "Usuario aprobado. Se le avisó por correo". Renombrar `reenviarAlta` a `reenviarMail` apuntando a `/usuarios/{id}/reenviar-mail` con el texto "Reenviar aviso". Reusar `.users-table`/`.user-item` para la lista; agregar solo lo mínimo en `AdminPanel.css`.

- [ ] **Step 4: Correr y ver que pasan**

Run: `cd frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false`
Expected: PASS, 6 suites.

- [ ] **Step 5: Commit**

```powershell
git add frontend/aura-frontend
git commit -m "feat(frontend): pestaña de solicitudes pendientes"
```

---

### Task 7: Configuración, Docker y verificación final

**Files:**
- Modify: `.env.example`
- Modify: `backend/project-aura/src/main/resources/application.properties` (local, no versionado — replicar la línea del `.example`)
- Modify: `frontend/aura-frontend/Dockerfile`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Configurar las variables**

`.env.example`: agregar

```
# Login con Google (Google Cloud Console > OAuth client ID, tipo Web)
# Mismo valor para backend y frontend. Vacio = login con Google deshabilitado.
GOOGLE_CLIENT_ID=
REACT_APP_GOOGLE_CLIENT_ID=
```

`Dockerfile`, antes de `RUN npm run build`:

```dockerfile
ARG REACT_APP_GOOGLE_CLIENT_ID
ENV REACT_APP_GOOGLE_CLIENT_ID=$REACT_APP_GOOGLE_CLIENT_ID
```

`docker-compose.yml`, servicio `frontend`:

```yaml
    build:
      context: ./frontend/aura-frontend
      args:
        REACT_APP_GOOGLE_CLIENT_ID: ${REACT_APP_GOOGLE_CLIENT_ID}
```

(el backend ya recibe todo el `.env` por `env_file`).

- [ ] **Step 2: Verificar el compose**

Run: `docker compose config --quiet`
Expected: sin salida, exit 0.

- [ ] **Step 3: Correr todo**

```powershell
cd backend/project-aura; .\mvnw.cmd test
cd ../../aura_chatbot; python -m pytest tests -q
cd ../frontend/aura-frontend; $env:CI="true"; npm test -- --watchAll=false
npm run build
```

Expected: backend 0 failures; IA 61 passed; frontend 6 suites en verde; build OK.

- [ ] **Step 4: Commit**

```powershell
git add .env.example docker-compose.yml frontend/aura-frontend/Dockerfile
git commit -m "chore(config): variables del login con Google"
```

---

## Manual check (después del código)

Con un Client ID real en `.env` y el mail cargado como test user en Google Cloud Console:

1. `npm start` + backend corriendo → `/login` muestra "Continuar con Google".
2. Cuenta no registrada → formulario de teléfono → "Tu solicitud está pendiente".
3. Entrar al panel con el super admin (`ADMIN_EMAIL` + `ADMIN_PASSWORD`) → pestaña Solicitudes → Aprobar.
4. Llega el mail; la persona vuelve a `/login`, entra con Google y cae en `/chat`.
5. Rechazar a otro usuario → al reintentar ve el mensaje de rechazo.

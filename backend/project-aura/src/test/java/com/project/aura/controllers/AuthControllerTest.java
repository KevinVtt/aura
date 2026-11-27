package com.project.aura.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.project.aura.config.TestSecurityConfig;
import com.project.aura.exception.ApiException;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.EstadoUsuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.GoogleAuthService;
import com.project.aura.services.UsuarioService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private GoogleAuthService googleAuthService;

    private Usuario usuarioAdmin() {
        Contacto contacto = Contacto.builder().email("admin@aura.com").build();
        return Usuario.builder()
            .id(1L)
            .nombre("Admin")
            .password("encoded")
            .rol(Rol.ADMIN)
            .activo(true)
            .contacto(contacto)
            .build();
    }

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

    @Test
    void login_normalizaElEmail() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuarioAdmin()));
        when(jwtUtil.generarToken("admin@aura.com", "ADMIN")).thenReturn("token-jwt");

        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Admin@AURA.com\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-jwt"));
    }

    @Test
    void login_exitoso_devuelve200ConToken() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuarioAdmin()));
        when(jwtUtil.generarToken("admin@aura.com", "ADMIN")).thenReturn("token-jwt");

        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.1"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aura.com\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-jwt"))
            .andExpect(jsonPath("$.nombre").value("Admin"))
            .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.2"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aura.com\",\"password\":\"incorrecta\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_rateLimit_sextoIntentoFallido_devuelve429() throws Exception {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        // 5 intentos fallidos desde la misma IP
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/auth/login")
                    .with(req -> { req.setRemoteAddr("10.0.0.99"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@aura.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        }

        // El 6to intento debe estar bloqueado
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.99"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aura.com\",\"password\":\"incorrecta\"}"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_emailVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.3"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"admin123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email").value("El email es obligatorio"));
    }

    @Test
    void login_emailNull_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.6"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"admin123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email").value("El email es obligatorio"));
    }

    @Test
    void login_emailSinDominioReal_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.5"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"otro@ejemplo\",\"password\":\"admin123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email").value("El email no tiene un formato válido"));
    }

    @Test
    void login_emailMalformado_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.4"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-es-un-email\",\"password\":\"admin123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email").value("El email no tiene un formato válido"));
    }

    @Test
    void login_passwordVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.5"); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aura.com\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.password").value("La contraseña es obligatoria"));
    }

    @Test
    void login_rateLimit_noSePuedeSaltarConXForwardedFor() throws Exception {
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/auth/login")
                    .with(req -> { req.setRemoteAddr("10.0.0.210"); return req; })
                    .header("X-Forwarded-For", "10.0.1." + i)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@aura.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        }

        // Aunque el header cambie, la IP real es la misma: el 6to intento se bloquea
        mockMvc.perform(post("/auth/login")
                .with(req -> { req.setRemoteAddr("10.0.0.210"); return req; })
                .header("X-Forwarded-For", "10.0.1.99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@aura.com\",\"password\":\"incorrecta\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
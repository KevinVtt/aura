package com.project.aura.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.project.aura.config.TestSecurityConfig;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.UsuarioService;

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
    private UsuarioService usuarioService;

    @MockBean
    private JwtUtil jwtUtil;

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

    @Test
    void register_admin_devuelve200ConToken() throws Exception {
        when(usuarioService.crear(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generarToken("nuevo@aura.com", "EMPLEADO")).thenReturn("token-nuevo");

        mockMvc.perform(post("/auth/register")
                .with(user("admin@aura.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"nuevo@aura.com\",\"telefono\":\"1234567890\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-nuevo"))
            .andExpect(jsonPath("$.nombre").value("Nuevo"))
            .andExpect(jsonPath("$.rol").value("EMPLEADO"));
    }

    @Test
    void register_empleado_devuelve403() throws Exception {
        mockMvc.perform(post("/auth/register")
                .with(user("emp@aura.com").roles("EMPLEADO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"nuevo@aura.com\"}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void register_sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"nuevo@aura.com\"}}"))
            .andExpect(status().isUnauthorized());
    }
}
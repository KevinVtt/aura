package com.project.aura.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.project.aura.config.TestSecurityConfig;
import com.project.aura.exception.ApiException;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.UsuarioService;

@WebMvcTest(UsuarioController.class)
@Import(TestSecurityConfig.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtUtil jwtUtil;

    private Usuario usuario() {
        Contacto contacto = Contacto.builder().email("juan@aura.com").build();
        return Usuario.builder()
            .id(1L)
            .nombre("Juan")
            .password("encoded")
            .rol(Rol.EMPLEADO)
            .activo(true)
            .contacto(contacto)
            .build();
    }

    private String bodyValido() {
        return "{\"nombre\":\"Juan\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
            + "\"contacto\":{\"email\":\"juan@aura.com\",\"telefono\":\"1234567890\"}}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_devuelve200() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of(usuario()));

        mockMvc.perform(get("/usuarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombre").value("Juan"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_listaVacia_devuelve200() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of());

        mockMvc.perform(get("/usuarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_devuelve200() throws Exception {
        when(usuarioService.crear(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyValido()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Juan"))
            .andExpect(jsonPath("$.rol").value("EMPLEADO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_emailDuplicado_devuelve400() throws Exception {
        when(usuarioService.crear(any(Usuario.class)))
            .thenThrow(new RuntimeException("El email ya está registrado"));

        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyValido()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("El email ya está registrado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_nombreInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"juan@aura.com\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.nombre").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_passwordCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Juan\",\"password\":\"corta\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"juan@aura.com\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.password").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_emailMalformado_devuelve400() throws Exception {
        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Juan\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"no-es-email\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_sinTelefono_devuelve200() throws Exception {
        when(usuarioService.crear(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Juan\",\"password\":\"password123\",\"rol\":\"EMPLEADO\","
                    + "\"contacto\":{\"email\":\"juan@aura.com\",\"telefono\":\"\"}}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void desactivar_devuelve204() throws Exception {
        when(usuarioService.desactivar(1L)).thenReturn(usuario());

        mockMvc.perform(delete("/usuarios/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void desactivar_idInexistente_devuelve400() throws Exception {
        when(usuarioService.desactivar(999L))
            .thenThrow(new RuntimeException("Usuario no encontrado"));

        mockMvc.perform(delete("/usuarios/999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Usuario no encontrado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void activar_devuelve204() throws Exception {
        when(usuarioService.activar(1L)).thenReturn(usuario());

        mockMvc.perform(patch("/usuarios/1/activar"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiarRol_devuelve204() throws Exception {
        mockMvc.perform(patch("/usuarios/1/rol")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMIN\"}"))
            .andExpect(status().isNoContent());

        verify(usuarioService).cambiarRol(1L, Rol.ADMIN);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiarRol_sinRol_devuelve400() throws Exception {
        mockMvc.perform(patch("/usuarios/1/rol")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":null}"))
            .andExpect(status().isBadRequest());

        verify(usuarioService, never()).cambiarRol(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cambiarRol_sobreElSuperAdmin_devuelve409() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT,
                "El super administrador no puede cambiar de rol"))
            .when(usuarioService).cambiarRol(9L, Rol.EMPLEADO);

        mockMvc.perform(patch("/usuarios/9/rol")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"EMPLEADO\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("El super administrador no puede cambiar de rol"));
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void empleado_cambiarRol_devuelve403() throws Exception {
        mockMvc.perform(patch("/usuarios/1/rol")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void empleado_listar_devuelve403() throws Exception {
        mockMvc.perform(get("/usuarios"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void empleado_crear_devuelve403() throws Exception {
        mockMvc.perform(post("/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyValido()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void empleado_desactivar_devuelve403() throws Exception {
        mockMvc.perform(delete("/usuarios/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/usuarios"))
            .andExpect(status().isUnauthorized());
    }
}
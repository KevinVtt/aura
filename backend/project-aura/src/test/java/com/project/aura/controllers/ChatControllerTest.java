package com.project.aura.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.project.aura.config.TestSecurityConfig;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Conversacion;
import com.project.aura.models.Mensaje;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Origen;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.ConversacionService;

@WebMvcTest(ChatController.class)
@Import(TestSecurityConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversacionService conversacionService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtUtil jwtUtil;

    private Usuario usuario() {
        Contacto contacto = Contacto.builder().email("admin@aura.com").build();
        return Usuario.builder()
            .id(1L)
            .nombre("Admin")
            .rol(Rol.ADMIN)
            .activo(true)
            .contacto(contacto)
            .build();
    }

    private Conversacion conversacion() {
        return Conversacion.builder()
            .id(1L)
            .sesionId("sesion-1")
            .usuario(usuario())
            .fechaInicio(LocalDateTime.now())
            .activa(true)
            .build();
    }

    private Mensaje mensajeBot() {
        return Mensaje.builder()
            .id(1L)
            .origen(Origen.BOT)
            .contenido("Respuesta de la IA")
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void iniciar_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.iniciar(any(Usuario.class))).thenReturn(conversacion());

        mockMvc.perform(post("/chat/iniciar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sesionId").value("sesion-1"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void iniciar_usuarioNoEncontrado_devuelve400() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/chat/iniciar"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Usuario no encontrado"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void enviarMensaje_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.enviarMensaje(eq("sesion-1"), eq("hola"), any(Usuario.class)))
            .thenReturn(mensajeBot());

        mockMvc.perform(post("/chat/sesion-1/mensaje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"hola\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido").value("Respuesta de la IA"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void enviarMensaje_preguntaVacia_devuelve400() throws Exception {
        mockMvc.perform(post("/chat/sesion-1/mensaje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.pregunta").value("La pregunta no puede estar vacía"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void enviarMensaje_preguntaNull_devuelve400() throws Exception {
        mockMvc.perform(post("/chat/sesion-1/mensaje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.pregunta").value("La pregunta no puede estar vacía"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void enviarMensaje_conversacionInexistente_devuelve400() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.enviarMensaje(eq("no-existe"), eq("hola"), any(Usuario.class)))
            .thenThrow(new RuntimeException("Conversación no encontrada"));

        mockMvc.perform(post("/chat/no-existe/mensaje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"hola\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Conversación no encontrada"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void obtenerMensajes_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.obtenerMensajes(eq("sesion-1"), any(Usuario.class)))
            .thenReturn(List.of(mensajeBot()));

        mockMvc.perform(get("/chat/sesion-1/mensajes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].contenido").value("Respuesta de la IA"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void obtenerMensajes_listaVacia_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.obtenerMensajes(eq("sesion-1"), any(Usuario.class)))
            .thenReturn(List.of());

        mockMvc.perform(get("/chat/sesion-1/mensajes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void cerrar_devuelve204() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        doNothing().when(conversacionService).cerrar(eq("sesion-1"), any(Usuario.class));

        mockMvc.perform(patch("/chat/sesion-1/cerrar"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void listarConversaciones_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.listarPorUsuario(1L)).thenReturn(List.of(conversacion()));

        mockMvc.perform(get("/chat/conversaciones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sesionId").value("sesion-1"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void listarConversaciones_listaVacia_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.listarPorUsuario(1L)).thenReturn(List.of());

        mockMvc.perform(get("/chat/conversaciones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void eliminar_devuelve204() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        doNothing().when(conversacionService).eliminar(eq("sesion-1"), any(Usuario.class));

        mockMvc.perform(delete("/chat/sesion-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void eliminarTodas_devuelve204() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(usuario()));
        doNothing().when(conversacionService).eliminarTodas(any(Usuario.class));

        mockMvc.perform(delete("/chat/conversaciones"))
            .andExpect(status().isNoContent());

        verify(conversacionService).eliminarTodas(any(Usuario.class));
    }

    @Test
    @WithMockUser(username = "emp@aura.com", roles = "EMPLEADO")
    void empleado_puedeIniciarConversacion() throws Exception {
        when(usuarioRepository.findByContactoEmail("emp@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.iniciar(any(Usuario.class))).thenReturn(conversacion());

        mockMvc.perform(post("/chat/iniciar"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "emp@aura.com", roles = "EMPLEADO")
    void empleado_puedeEnviarMensaje() throws Exception {
        when(usuarioRepository.findByContactoEmail("emp@aura.com")).thenReturn(Optional.of(usuario()));
        when(conversacionService.enviarMensaje(eq("sesion-1"), eq("hola"), any(Usuario.class)))
            .thenReturn(mensajeBot());

        mockMvc.perform(post("/chat/sesion-1/mensaje")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"hola\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/chat/conversaciones"))
            .andExpect(status().isUnauthorized());
    }
}
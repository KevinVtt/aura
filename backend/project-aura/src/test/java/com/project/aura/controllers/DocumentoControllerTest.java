package com.project.aura.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.project.aura.config.TestSecurityConfig;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Documento;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.DocumentoService;

@WebMvcTest(DocumentoController.class)
@Import(TestSecurityConfig.class)
class DocumentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentoService documentoService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtUtil jwtUtil;

    private Usuario admin() {
        Contacto contacto = Contacto.builder().email("admin@aura.com").build();
        return Usuario.builder()
            .id(1L)
            .nombre("Admin")
            .rol(Rol.ADMIN)
            .activo(true)
            .contacto(contacto)
            .build();
    }

    private Documento documento() {
        return Documento.builder()
            .id(1L)
            .nombreArchivo("manual.pdf")
            .admin(admin())
            .fechaSubida(LocalDateTime.now())
            .activo(true)
            .build();
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void listar_devuelve200() throws Exception {
        when(documentoService.listarActivos()).thenReturn(List.of(documento()));

        mockMvc.perform(get("/documentos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombreArchivo").value("manual.pdf"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void listar_listaVacia_devuelve200() throws Exception {
        when(documentoService.listarActivos()).thenReturn(List.of());

        mockMvc.perform(get("/documentos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void subirPdf_devuelve200() throws Exception {
        when(usuarioRepository.findByContactoEmail("admin@aura.com")).thenReturn(Optional.of(admin()));
        when(documentoService.subir(eq("manual.pdf"), any(byte[].class), any(Usuario.class)))
            .thenReturn(documento());

        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "manual.pdf", "application/pdf", "%PDF-1.4 contenido falso".getBytes());

        mockMvc.perform(multipart("/documentos/subir").file(archivo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombreArchivo").value("manual.pdf"));
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void subirNoPdf_devuelve400() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "nota.txt", "text/plain", "contenido de texto".getBytes());

        mockMvc.perform(multipart("/documentos/subir").file(archivo))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void subirNombreArchivoMuyLargo_devuelve400() throws Exception {
        String nombreLargo = "a".repeat(300) + ".pdf";
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", nombreLargo, "application/pdf", "%PDF".getBytes());

        mockMvc.perform(multipart("/documentos/subir").file(archivo))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void eliminar_devuelve204() throws Exception {
        doNothing().when(documentoService).eliminar(1L);

        mockMvc.perform(delete("/documentos/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@aura.com", roles = "ADMIN")
    void eliminar_idInexistente_devuelve400() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Documento no encontrado"))
            .when(documentoService).eliminar(999L);

        mockMvc.perform(delete("/documentos/999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Documento no encontrado"));
    }

    @Test
    @WithMockUser(username = "emp@aura.com", roles = "EMPLEADO")
    void empleado_listar_devuelve403() throws Exception {
        mockMvc.perform(get("/documentos"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@aura.com", roles = "EMPLEADO")
    void empleado_subir_devuelve403() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "manual.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(multipart("/documentos/subir").file(archivo))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@aura.com", roles = "EMPLEADO")
    void empleado_eliminar_devuelve403() throws Exception {
        mockMvc.perform(delete("/documentos/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticacion_devuelve401() throws Exception {
        mockMvc.perform(get("/documentos"))
            .andExpect(status().isUnauthorized());
    }
}
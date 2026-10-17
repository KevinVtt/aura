package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.project.aura.models.Contacto;
import com.project.aura.models.Conversacion;
import com.project.aura.models.Mensaje;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Origen;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.ConversacionRepository;
import com.project.aura.repositories.MensajeRepository;

@ExtendWith(MockitoExtension.class)
class ConversacionServiceTest {

    @Mock
    private ConversacionRepository conversacionRepository;

    @Mock
    private MensajeRepository mensajeRepository;

    @Mock
    private FastAPIClient fastAPIClient;

    @InjectMocks
    private ConversacionService conversacionService;

    private Usuario usuario;
    private Conversacion conversacion;

    @BeforeEach
    void setUp() {
        Contacto contacto = Contacto.builder().email("admin@aura.com").build();
        usuario = Usuario.builder()
            .id(1L)
            .nombre("Admin")
            .rol(Rol.ADMIN)
            .activo(true)
            .contacto(contacto)
            .build();
        conversacion = Conversacion.builder()
            .id(1L)
            .sesionId("sesion-1")
            .usuario(usuario)
            .fechaInicio(LocalDateTime.now())
            .activa(true)
            .build();
    }

    @Test
    void iniciar_creaConversacionActivaConSesionId() {
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversacion resultado = conversacionService.iniciar(usuario);

        assertNotNull(resultado.getSesionId());
        assertFalse(resultado.getSesionId().isBlank());
        assertTrue(resultado.getActiva());
        assertEquals(usuario, resultado.getUsuario());
        verify(conversacionRepository).save(any(Conversacion.class));
    }

    @Test
    void enviarMensaje_guardaMensajeEmpleadoYBot() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        when(fastAPIClient.chat("sesion-1", "hola"))
            .thenReturn(new FastAPIClient.RespuestaIA("Respuesta de la IA", List.of("manual.pdf")));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = conversacionService.enviarMensaje("sesion-1", "hola", usuario);

        assertNotNull(resultado);
        assertEquals("Respuesta de la IA", resultado.getContenido());
        assertEquals("manual.pdf", resultado.getFuentes());
        assertEquals(Origen.BOT, resultado.getOrigen());
        assertNull(resultado.getUsuario());
        verify(mensajeRepository, times(2)).save(any(Mensaje.class));
        verify(fastAPIClient).chat("sesion-1", "hola");
    }

    @Test
    void enviarMensaje_conversacionNoEncontrada_lanzaExcepcion() {
        when(conversacionRepository.findBySesionId("inexistente")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> conversacionService.enviarMensaje("inexistente", "hola", usuario));

        assertEquals("Conversación no encontrada", ex.getMessage());
        verify(fastAPIClient, never()).chat(any(), any());
    }

    @Test
    void enviarMensaje_preguntaVacia_esAceptadaSinValidacion() {
        // El servicio NO valida preguntas vacías: la validación solo existe en el controller
        // (PreguntaRequest @NotBlank). Este test documenta el comportamiento actual.
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        when(fastAPIClient.chat("sesion-1", ""))
            .thenReturn(new FastAPIClient.RespuestaIA("Respuesta", List.of()));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        Mensaje resultado = conversacionService.enviarMensaje("sesion-1", "", usuario);

        assertNotNull(resultado);
        verify(fastAPIClient).chat("sesion-1", "");
    }

    @Test
    void enviarMensaje_errorFastAPI_sePropaga() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        when(fastAPIClient.chat("sesion-1", "hola"))
            .thenThrow(new RuntimeException("FastAPI caído"));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> conversacionService.enviarMensaje("sesion-1", "hola", usuario));

        assertEquals("FastAPI caído", ex.getMessage());
        // El mensaje del empleado se guardó antes del error; el del bot no
        verify(mensajeRepository, times(1)).save(any(Mensaje.class));
    }

    @Test
    void obtenerMensajes_devuelveListaOrdenada() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        Mensaje m1 = Mensaje.builder().id(1L).contenido("hola").origen(Origen.EMPLEADO)
            .timestamp(LocalDateTime.now()).build();
        Mensaje m2 = Mensaje.builder().id(2L).contenido("respuesta").origen(Origen.BOT)
            .timestamp(LocalDateTime.now()).build();
        when(mensajeRepository.findByConversacionIdOrderByTimestampAsc(1L)).thenReturn(List.of(m1, m2));

        List<Mensaje> mensajes = conversacionService.obtenerMensajes("sesion-1", usuario);

        assertEquals(2, mensajes.size());
        verify(mensajeRepository).findByConversacionIdOrderByTimestampAsc(1L);
    }

    @Test
    void obtenerMensajes_listaVacia_devuelveListaVacia() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        when(mensajeRepository.findByConversacionIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        List<Mensaje> mensajes = conversacionService.obtenerMensajes("sesion-1", usuario);

        assertTrue(mensajes.isEmpty());
    }

    @Test
    void obtenerMensajes_conversacionNoEncontrada_lanzaExcepcion() {
        when(conversacionRepository.findBySesionId("inexistente")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> conversacionService.obtenerMensajes("inexistente", usuario));
    }

    @Test
    void obtenerMensajes_otroUsuario_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(2L).nombre("Otro").rol(Rol.EMPLEADO)
            .activo(true).contacto(Contacto.builder().email("otro@aura.com").build()).build();
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> conversacionService.obtenerMensajes("sesion-1", otro));

        assertEquals("No tenés permiso para acceder a esta conversación", ex.getMessage());
        verify(mensajeRepository, never()).findByConversacionIdOrderByTimestampAsc(any());
    }

    @Test
    void enviarMensaje_otroUsuario_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(2L).nombre("Otro").rol(Rol.EMPLEADO)
            .activo(true).contacto(Contacto.builder().email("otro@aura.com").build()).build();
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));

        assertThrows(RuntimeException.class,
            () -> conversacionService.enviarMensaje("sesion-1", "hola", otro));

        verify(fastAPIClient, never()).chat(any(), any());
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    void cerrar_otroUsuario_lanzaExcepcion() {
        Usuario otro = Usuario.builder().id(2L).nombre("Otro").rol(Rol.EMPLEADO)
            .activo(true).contacto(Contacto.builder().email("otro@aura.com").build()).build();
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));

        assertThrows(RuntimeException.class,
            () -> conversacionService.cerrar("sesion-1", otro));

        assertTrue(conversacion.getActiva());
        verify(conversacionRepository, never()).save(any());
    }

    @Test
    void cerrar_marcaConversacionInactivaConFechaFin() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));

        conversacionService.cerrar("sesion-1", usuario);

        assertFalse(conversacion.getActiva());
        assertNotNull(conversacion.getFechaFin());
        verify(conversacionRepository).save(conversacion);
    }

    @Test
    void cerrar_conversacionNoEncontrada_lanzaExcepcion() {
        when(conversacionRepository.findBySesionId("inexistente")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> conversacionService.cerrar("inexistente", usuario));
    }

    @Test
    void listarPorUsuario_devuelveConversaciones() {
        when(conversacionRepository.findByUsuarioIdOrderByFechaInicioDesc(1L))
            .thenReturn(List.of(conversacion));

        List<Conversacion> resultado = conversacionService.listarPorUsuario(1L);

        assertEquals(1, resultado.size());
        verify(conversacionRepository).findByUsuarioIdOrderByFechaInicioDesc(1L);
    }

    @Test
    void listarPorUsuario_sinConversaciones_devuelveListaVacia() {
        when(conversacionRepository.findByUsuarioIdOrderByFechaInicioDesc(1L)).thenReturn(List.of());

        List<Conversacion> resultado = conversacionService.listarPorUsuario(1L);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void listarPorUsuario_usaLaPrimeraPreguntaComoTitulo() {
        when(conversacionRepository.findByUsuarioIdOrderByFechaInicioDesc(1L))
            .thenReturn(List.of(conversacion));
        Mensaje primera = Mensaje.builder().id(1L).contenido("¿Cuántos días de vacaciones?")
            .origen(Origen.EMPLEADO).timestamp(LocalDateTime.now()).build();
        when(mensajeRepository.findFirstByConversacionIdAndOrigenOrderByTimestampAsc(1L, Origen.EMPLEADO))
            .thenReturn(Optional.of(primera));

        List<Conversacion> resultado = conversacionService.listarPorUsuario(1L);

        assertEquals("¿Cuántos días de vacaciones?", resultado.get(0).getTitulo());
    }

    @Test
    void listarPorUsuario_recortaUnTituloLargo() {
        when(conversacionRepository.findByUsuarioIdOrderByFechaInicioDesc(1L))
            .thenReturn(List.of(conversacion));
        Mensaje primera = Mensaje.builder().id(1L).contenido("a".repeat(80))
            .origen(Origen.EMPLEADO).timestamp(LocalDateTime.now()).build();
        when(mensajeRepository.findFirstByConversacionIdAndOrigenOrderByTimestampAsc(1L, Origen.EMPLEADO))
            .thenReturn(Optional.of(primera));

        String titulo = conversacionService.listarPorUsuario(1L).get(0).getTitulo();

        assertEquals(61, titulo.length());
        assertTrue(titulo.endsWith("…"));
    }

    @Test
    void eliminar_eliminaConversacionDelMismoUsuario() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));

        conversacionService.eliminar("sesion-1", usuario);

        verify(fastAPIClient).limpiarHistorial("sesion-1");
        verify(conversacionRepository).delete(conversacion);
    }

    @Test
    void eliminar_otroUsuario_lanzaExcepcion() {
        Contacto otroContacto = Contacto.builder().email("otro@aura.com").build();
        Usuario otro = Usuario.builder()
            .id(2L)
            .nombre("Otro")
            .rol(Rol.EMPLEADO)
            .activo(true)
            .contacto(otroContacto)
            .build();
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> conversacionService.eliminar("sesion-1", otro));

        assertEquals("No tenés permiso para eliminar esta conversación", ex.getMessage());
        verify(conversacionRepository, never()).delete(any());
        verify(fastAPIClient, never()).limpiarHistorial(any());
    }

    @Test
    void eliminar_conversacionNoEncontrada_lanzaExcepcion() {
        when(conversacionRepository.findBySesionId("inexistente")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> conversacionService.eliminar("inexistente", usuario));
    }

    @Test
    void eliminar_errorFastAPI_noImpideEliminacion() {
        when(conversacionRepository.findBySesionId("sesion-1")).thenReturn(Optional.of(conversacion));
        doThrow(new RuntimeException("FastAPI caído")).when(fastAPIClient).limpiarHistorial("sesion-1");

        conversacionService.eliminar("sesion-1", usuario);

        verify(conversacionRepository).delete(conversacion);
    }
}
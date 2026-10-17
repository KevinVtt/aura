package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.project.aura.exception.ApiException;
import com.project.aura.models.Conversacion;
import com.project.aura.models.Mensaje;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Origen;
import com.project.aura.repositories.ConversacionRepository;
import com.project.aura.repositories.MensajeRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversacionService {

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final FastAPIClient fastAPIClient;

    public Conversacion iniciar(Usuario usuario) {
        Conversacion conversacion = Conversacion.builder()
            .sesionId(UUID.randomUUID().toString())
            .usuario(usuario)
            .fechaInicio(LocalDateTime.now())
            .activa(true)
            .build();
        return conversacionRepository.save(conversacion);
    }

    public Mensaje enviarMensaje(String sesionId, String pregunta, Usuario usuario) {
        Conversacion conversacion = conversacionDe(sesionId, usuario);

        // 1. Guardar mensaje del empleado
        Mensaje mensajeEmpleado = Mensaje.builder()
            .conversacion(conversacion)
            .origen(Origen.EMPLEADO)
            .usuario(usuario)
            .contenido(pregunta)
            .timestamp(LocalDateTime.now())
            .build();
        mensajeRepository.save(mensajeEmpleado);

        // 2. Llamar a FastAPI y obtener respuesta
        FastAPIClient.RespuestaIA respuestaIA = fastAPIClient.chat(sesionId, pregunta);

        // 3. Guardar respuesta del bot
        Mensaje mensajeBot = Mensaje.builder()
            .conversacion(conversacion)
            .origen(Origen.BOT)
            .usuario(null)
            .contenido(respuestaIA.respuesta())
            // shortcut: nombres separados por coma; un PDF con coma en el nombre
            // se partiria en dos chips. Pasar a JSON si aparecen esos nombres.
            .fuentes(respuestaIA.fuentes().isEmpty()
                ? null : String.join(",", respuestaIA.fuentes()))
            .timestamp(LocalDateTime.now())
            .build();
        mensajeRepository.save(mensajeBot);

        return mensajeBot;
    }

    public List<Mensaje> obtenerMensajes(String sesionId, Usuario usuario) {
        Conversacion conversacion = conversacionDe(sesionId, usuario);
        return mensajeRepository
            .findByConversacionIdOrderByTimestampAsc(conversacion.getId());
    }

    public void cerrar(String sesionId, Usuario usuario) {
        Conversacion conversacion = conversacionDe(sesionId, usuario);
        conversacion.setActiva(false);
        conversacion.setFechaFin(LocalDateTime.now());
        conversacionRepository.save(conversacion);
    }

    public List<Conversacion> listarPorUsuario(Long usuarioId) {
        List<Conversacion> conversaciones = conversacionRepository
            .findByUsuarioIdOrderByFechaInicioDesc(usuarioId);

        // El sidebar mostraba el UUID de la sesion, que no le dice nada a nadie:
        // el titulo es la primera pregunta de la conversacion.
        for (Conversacion conversacion : conversaciones) {
            mensajeRepository
                .findFirstByConversacionIdAndOrigenOrderByTimestampAsc(
                    conversacion.getId(), Origen.EMPLEADO)
                .ifPresent(primero ->
                    conversacion.setTitulo(recortar(primero.getContenido())));
        }
        return conversaciones;
    }

    private String recortar(String texto) {
        return texto.length() > 60 ? texto.substring(0, 60).trim() + "…" : texto;
    }

    public void eliminar(String sesionId, Usuario usuario) {
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversación no encontrada"));

        if (!conversacion.getUsuario().getId().equals(usuario.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tenés permiso para eliminar esta conversación");
        }

        try {
            fastAPIClient.limpiarHistorial(sesionId);
        } catch (Exception e) {
        }

        conversacionRepository.delete(conversacion);
    }

    // Reusa eliminar() conversación por conversación: así se limpia también el
    // historial de la IA y no hay que duplicar ese manejo de errores.
    public void eliminarTodas(Usuario usuario) {
        List<Conversacion> conversaciones = conversacionRepository
            .findByUsuarioIdOrderByFechaInicioDesc(usuario.getId());

        for (Conversacion conversacion : conversaciones) {
            eliminar(conversacion.getSesionId(), usuario);
        }
    }

    // Devuelve la conversación solo si pertenece al usuario que la pide (evita el IDOR).
    private Conversacion conversacionDe(String sesionId, Usuario usuario) {
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversación no encontrada"));

        if (!conversacion.getUsuario().getId().equals(usuario.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tenés permiso para acceder a esta conversación");
        }
        return conversacion;
    }
}
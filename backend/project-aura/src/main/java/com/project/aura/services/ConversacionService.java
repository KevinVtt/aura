package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

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
        String respuesta = fastAPIClient.chat(sesionId, pregunta);

        // 3. Guardar respuesta del bot
        Mensaje mensajeBot = Mensaje.builder()
            .conversacion(conversacion)
            .origen(Origen.BOT)
            .usuario(null)
            .contenido(respuesta)
            .timestamp(LocalDateTime.now())
            .build();
        mensajeRepository.save(mensajeBot);

        return mensajeBot;
    }

    public List<Mensaje> obtenerMensajes(String sesionId) {
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));
        return mensajeRepository
            .findByConversacionIdOrderByTimestampAsc(conversacion.getId());
    }

    public void cerrar(String sesionId) {
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));
        conversacion.setActiva(false);
        conversacion.setFechaFin(LocalDateTime.now());
        conversacionRepository.save(conversacion);
    }

    public List<Conversacion> listarPorUsuario(Long usuarioId) {
    return conversacionRepository
        .findByUsuarioIdOrderByFechaInicioDesc(usuarioId);
    }

    public void eliminar(String sesionId, Usuario usuario) {
        Conversacion conversacion = conversacionRepository
            .findBySesionId(sesionId)
            .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));

        if (!conversacion.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("No tenés permiso para eliminar esta conversación");
        }

        try {
            fastAPIClient.limpiarHistorial(sesionId);
        } catch (Exception e) {
        }

        conversacionRepository.delete(conversacion);
    }
}
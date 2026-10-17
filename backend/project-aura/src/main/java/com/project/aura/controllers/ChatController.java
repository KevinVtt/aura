package com.project.aura.controllers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.project.aura.dto.PreguntaRequest;
import com.project.aura.exception.ApiException;
import com.project.aura.models.Conversacion;
import com.project.aura.models.Mensaje;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.ConversacionService;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ChatController {

    private final ConversacionService conversacionService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/iniciar")
    public ResponseEntity<Conversacion> iniciar() {
        return ResponseEntity.ok(conversacionService.iniciar(usuarioActual()));
    }

    @PostMapping("/{sesionId}/mensaje")
    public ResponseEntity<Mensaje> enviarMensaje(
            @PathVariable String sesionId,
            @Valid @RequestBody PreguntaRequest pregunta) {

        Mensaje respuesta = conversacionService
            .enviarMensaje(sesionId, pregunta.getPregunta(), usuarioActual());

        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{sesionId}/mensajes")
    public ResponseEntity<List<Mensaje>> obtenerMensajes(
            @PathVariable String sesionId) {
        return ResponseEntity.ok(conversacionService.obtenerMensajes(sesionId, usuarioActual()));
    }

    @PatchMapping("/{sesionId}/cerrar")
    public ResponseEntity<Void> cerrar(@PathVariable String sesionId) {
        conversacionService.cerrar(sesionId, usuarioActual());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversaciones")
    public ResponseEntity<List<Conversacion>> listarConversaciones() {
        return ResponseEntity.ok(
            conversacionService.listarPorUsuario(usuarioActual().getId())
        );
    }

    @DeleteMapping("/{sesionId}")
    public ResponseEntity<Void> eliminarConversacion(
            @PathVariable String sesionId) {
        conversacionService.eliminar(sesionId, usuarioActual());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversaciones")
    public ResponseEntity<Void> eliminarTodas() {
        conversacionService.eliminarTodas(usuarioActual());
        return ResponseEntity.noContent().build();
    }

    private Usuario usuarioActual() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        return usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));
    }
}

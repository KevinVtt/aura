package com.project.aura.controllers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.project.aura.dto.PreguntaRequest;
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
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return ResponseEntity.ok(conversacionService.iniciar(usuario));
    }

    @PostMapping("/{sesionId}/mensaje")
    public ResponseEntity<Mensaje> enviarMensaje(
            @PathVariable String sesionId,
            @Valid @RequestBody PreguntaRequest pregunta) {

        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Mensaje respuesta = conversacionService
            .enviarMensaje(sesionId, pregunta.getPregunta(), usuario);

        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{sesionId}/mensajes")
    public ResponseEntity<List<Mensaje>> obtenerMensajes(
            @PathVariable String sesionId) {
        return ResponseEntity.ok(conversacionService.obtenerMensajes(sesionId));
    }

    @PatchMapping("/{sesionId}/cerrar")
    public ResponseEntity<Void> cerrar(@PathVariable String sesionId) {
        conversacionService.cerrar(sesionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversaciones")
    public ResponseEntity<List<Conversacion>> listarConversaciones() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return ResponseEntity.ok(
            conversacionService.listarPorUsuario(usuario.getId())
        );
    }

    @DeleteMapping("/{sesionId}")
    public ResponseEntity<Void> eliminarConversacion(
            @PathVariable String sesionId) {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        conversacionService.eliminar(sesionId, usuario);
        return ResponseEntity.noContent().build();
    }
}
package com.project.aura.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.services.UsuarioService;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<Usuario>> listar() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<Usuario> crear(
            @Valid @RequestBody Usuario request) {

        Contacto contacto = Contacto.builder()
            .email(request.getContacto().getEmail())
            .telefono(request.getContacto().getTelefono())
            .build();

        Usuario usuario = Usuario.builder()
            .nombre(request.getNombre())
            .password(request.getPassword())
            .rol(request.getRol())
            .contacto(contacto)
            .build();

        return ResponseEntity.ok(usuarioService.crear(usuario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        usuarioService.activar(id);
        return ResponseEntity.noContent().build();
    }
}
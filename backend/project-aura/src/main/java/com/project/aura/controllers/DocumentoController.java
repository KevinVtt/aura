package com.project.aura.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.aura.exception.ApiException;
import com.project.aura.models.Documento;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.DocumentoService;

import java.util.List;

@RestController
@RequestMapping("/documentos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<Documento>> listar() {
        return ResponseEntity.ok(documentoService.listarActivos());
    }

    @PostMapping("/subir")
    public ResponseEntity<Documento> subir(
            @RequestParam("archivo") MultipartFile archivo) throws Exception {

        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Solo se permiten archivos PDF");
        }
        if (nombre.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "El nombre del archivo no puede superar los 255 caracteres");
        }

        Usuario admin = usuarioRepository
            .findByContactoEmail(SecurityContextHolder.getContext()
                .getAuthentication()
                .getName())
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));

        Documento documento = documentoService.subir(
            nombre,
            archivo.getBytes(),
            admin
        );

        return ResponseEntity.ok(documento);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
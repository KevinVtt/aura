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
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/documentos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DocumentoController {

    private static final Set<String> EXTENSIONES = Set.of(".pdf", ".docx", ".xlsx");

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
        int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
        String extension = punto < 0 ? "" : nombre.substring(punto).toLowerCase(Locale.ROOT);

        if (!EXTENSIONES.contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Solo se permiten archivos PDF, Word (.docx) o Excel (.xlsx)");
        }
        if (nombre.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "El nombre del archivo no puede superar los 255 caracteres");
        }
        if (archivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }

        byte[] contenido = archivo.getBytes();
        if (!firmaValida(extension, contenido)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "El archivo no parece un " + extension.substring(1).toUpperCase() + " válido");
        }

        Usuario admin = usuarioRepository
            .findByContactoEmail(SecurityContextHolder.getContext()
                .getAuthentication()
                .getName())
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));

        Documento documento = documentoService.subir(
            nombre,
            contenido,
            admin
        );

        return ResponseEntity.ok(documento);
    }

    /** La extensión la elige quien sube el archivo; los primeros bytes no. */
    private static boolean firmaValida(String extension, byte[] c) {
        if (extension.equals(".pdf")) {
            return c.length >= 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F';
        }
        // .docx y .xlsx son archivos ZIP.
        return c.length >= 2 && c[0] == 'P' && c[1] == 'K';
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
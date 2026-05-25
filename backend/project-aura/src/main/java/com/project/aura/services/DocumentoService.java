package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.project.aura.models.Documento;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.DocumentoRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final FastAPIClient fastAPIClient;

    public List<Documento> listarActivos() {
        return documentoRepository.findByActivoTrue();
    }

    public Documento subir(String nombreArchivo, byte[] contenido, Usuario admin) {
        // 1. Manda el PDF a FastAPI para procesarlo
        fastAPIClient.subirDocumento(nombreArchivo, contenido);

        // 2. Registra el documento en PostgreSQL
        Documento documento = Documento.builder()
            .nombreArchivo(nombreArchivo)
            .admin(admin)
            .fechaSubida(LocalDateTime.now())
            .activo(true)
            .build();

        return documentoRepository.save(documento);
    }

    public void eliminar(Long id) {
        Documento documento = documentoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        // 1. Elimina de ChromaDB via FastAPI
        fastAPIClient.eliminarDocumento(documento.getNombreArchivo());

        // 2. Marca como inactivo en PostgreSQL
        documento.setActivo(false);
        documentoRepository.save(documento);
    }
}
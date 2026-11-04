package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.project.aura.exception.ApiException;
import com.project.aura.models.Documento;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.DocumentoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

        // 2. Registra el documento en PostgreSQL. Resubir el mismo nombre reutiliza la
        // fila activa: con dos filas, borrar una sola deja la otra apuntando a un
        // documento que la IA ya no tiene.
        Optional<Documento> existente = documentoRepository
            .findFirstByNombreArchivoAndActivoTrue(nombreArchivo);
        Documento documento = existente
            .orElseGet(() -> Documento.builder().nombreArchivo(nombreArchivo).build());

        documento.setAdmin(admin);
        documento.setFechaSubida(LocalDateTime.now());
        documento.setTamanoBytes((long) contenido.length);
        documento.setActivo(true);

        try {
            return documentoRepository.save(documento);
        } catch (RuntimeException e) {
            // Sin fila en la base el documento queda invisible e imborrable desde
            // el panel: se deshace la indexacion. En una resubida no se toca la
            // copia anterior, que sigue registrada.
            if (existente.isEmpty()) {
                try {
                    fastAPIClient.eliminarDocumento(nombreArchivo);
                } catch (RuntimeException ignored) {
                }
            }
            throw e;
        }
    }

    public void eliminar(Long id) {
        Documento documento = documentoRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Documento no encontrado"));

        // 1. Elimina de ChromaDB via FastAPI
        fastAPIClient.eliminarDocumento(documento.getNombreArchivo());

        // 2. Marca como inactivo en PostgreSQL
        documento.setActivo(false);
        documentoRepository.save(documento);
    }
}
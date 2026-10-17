package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.project.aura.models.Documento;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.DocumentoRepository;

class DocumentoServiceTest {

    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final FastAPIClient fastAPIClient = mock(FastAPIClient.class);
    private final DocumentoService documentoService = new DocumentoService(documentoRepository, fastAPIClient);
    private final Usuario admin = new Usuario();

    @Test
    void subir_nombreNuevo_registraUnDocumentoActivo() {
        when(documentoRepository.findFirstByNombreArchivoAndActivoTrue("manual.pdf")).thenReturn(Optional.empty());
        when(documentoRepository.save(any(Documento.class))).thenAnswer(inv -> inv.getArgument(0));

        Documento guardado = documentoService.subir("manual.pdf", new byte[] { 1 }, admin);

        verify(fastAPIClient).subirDocumento("manual.pdf", new byte[] { 1 });
        assertEquals("manual.pdf", guardado.getNombreArchivo());
        assertEquals(true, guardado.getActivo());
        assertSame(admin, guardado.getAdmin());
    }

    @Test
    void subir_mismoNombre_reutilizaLaFilaActivaEnVezDeDuplicarla() {
        Documento existente = Documento.builder()
            .id(7L)
            .nombreArchivo("manual.pdf")
            .fechaSubida(LocalDateTime.now().minusDays(1))
            .build();
        when(documentoRepository.findFirstByNombreArchivoAndActivoTrue("manual.pdf")).thenReturn(Optional.of(existente));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(inv -> inv.getArgument(0));

        Documento guardado = documentoService.subir("manual.pdf", new byte[] { 2 }, admin);

        assertSame(existente, guardado, "debe actualizar la fila existente, no crear otra");
        assertEquals(7L, guardado.getId());
        assertEquals("manual.pdf", guardado.getNombreArchivo());
        assertEquals(true, guardado.getActivo());
    }
}

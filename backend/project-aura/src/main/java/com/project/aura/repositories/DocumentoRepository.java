package com.project.aura.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.project.aura.models.Documento;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByActivoTrue();

    Optional<Documento> findByNombreArchivo(String nombreArchivo);

    boolean existsByNombreArchivo(String nombreArchivo);
}
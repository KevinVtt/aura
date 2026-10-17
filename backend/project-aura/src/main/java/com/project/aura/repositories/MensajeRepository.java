package com.project.aura.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.aura.models.Mensaje;
import com.project.aura.models.enums.Origen;

import java.util.List;
import java.util.Optional;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    List<Mensaje> findByConversacionIdOrderByTimestampAsc(Long conversacionId);

    // Primera pregunta de una conversacion (la que se muestra como titulo).
    Optional<Mensaje> findFirstByConversacionIdAndOrigenOrderByTimestampAsc(
        Long conversacionId, Origen origen);
}
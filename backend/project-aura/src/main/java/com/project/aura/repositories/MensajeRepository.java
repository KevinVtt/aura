package com.project.aura.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.aura.models.Mensaje;
import com.project.aura.models.enums.Origen;

import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    List<Mensaje> findByConversacionIdOrderByTimestampAsc(Long conversacionId);

    // Todas las primeras preguntas de varias conversaciones en una sola consulta
    // (el historial hacia una consulta por conversacion para armar el titulo).
    @Query("select m from Mensaje m where m.conversacion.id in :ids and m.origen = :origen "
         + "order by m.timestamp asc")
    List<Mensaje> buscarTitulos(
        @Param("ids") List<Long> ids, @Param("origen") Origen origen);
}
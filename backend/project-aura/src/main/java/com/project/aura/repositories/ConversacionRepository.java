package com.project.aura.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.aura.models.Conversacion;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    Optional<Conversacion> findBySesionId(String sesionId);

    List<Conversacion> findByUsuarioId(Long usuarioId);

    List<Conversacion> findByActivaTrue();

    List<Conversacion> findByUsuarioIdOrderByFechaInicioDesc(Long usuarioId);
}
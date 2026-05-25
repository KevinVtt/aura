package com.project.aura.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.project.aura.models.Contacto;

@Repository
public interface ContactoRepository extends JpaRepository<Contacto, Long> {
    Optional<Contacto> findByEmail(String email);
}
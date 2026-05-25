package com.project.aura.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.project.aura.models.Usuario;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Navega Usuario → Contacto → email
    Optional<Usuario> findByContactoEmail(String email);

    boolean existsByContactoEmail(String email);
}
package com.project.aura.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${aura.admin.email:admin@aura.com}")
    private String adminEmail;

    @Value("${aura.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        // Sin password configurada no se siembra un admin con credenciales conocidas.
        if (adminPassword.isBlank()) {
            throw new IllegalStateException(
                "La base no tiene usuarios y ADMIN_PASSWORD no está definida. " +
                "Definí ADMIN_PASSWORD (y opcionalmente ADMIN_EMAIL) para crear el primer administrador.");
        }

        Contacto contacto = Contacto.builder()
                .email(adminEmail)
                .build();

        Usuario admin = Usuario.builder()
                .nombre("Admin")
                .password(passwordEncoder.encode(adminPassword))
                .rol(Rol.ADMIN)
                .activo(true)
                .contacto(contacto)
                .build();

        usuarioRepository.save(admin);
        log.info("Usuario administrador inicial creado: {}", adminEmail);
    }
}

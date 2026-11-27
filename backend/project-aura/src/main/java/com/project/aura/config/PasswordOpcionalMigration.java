package com.project.aura.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Las cuentas con Google ya no tienen contraseña, pero Hibernate con
 * ddl-auto=update nunca quita un NOT NULL existente: en una base creada por
 * una version anterior el alta fallaría con 500. Esto lo destraba una vez y
 * no hace nada si la columna ya es opcional.
 *
 * shortcut: SQL de Postgres (el proyecto usa Postgres en todos lados); si
 * algun dia se agrega otro motor, moverlo a una migracion real (Flyway).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class PasswordOpcionalMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE usuarios ALTER COLUMN password DROP NOT NULL");
            log.info("Columna usuarios.password: se aseguro que acepta NULL");
        } catch (DataAccessException e) {
            log.warn("No se pudo quitar el NOT NULL de usuarios.password: {}", e.getMessage());
        }
    }
}

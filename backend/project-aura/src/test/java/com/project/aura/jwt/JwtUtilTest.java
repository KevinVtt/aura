package com.project.aura.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilTest {

    private static final String SECRET = "4ur4Ch4tb0tS3cr3tK3y2025Unl4Pr0y3ct0F1n4lS3gur0";
    private static final long EXPIRATION = 86400000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION);
    }

    @Test
    void generarToken_devuelveTokenValido() {
        String token = jwtUtil.generarToken("admin@aura.com", "ADMIN");

        assertNotNull(token);
        assertTrue(jwtUtil.validarToken(token));
    }

    @Test
    void obtenerEmail_devuelveEmailCorrecto() {
        String token = jwtUtil.generarToken("admin@aura.com", "ADMIN");

        assertEquals("admin@aura.com", jwtUtil.obtenerEmail(token));
    }

    @Test
    void obtenerRol_devuelveRolCorrecto() {
        String token = jwtUtil.generarToken("admin@aura.com", "ADMIN");

        assertEquals("ADMIN", jwtUtil.obtenerRol(token));
    }

    @Test
    void tokenExpirado_noEsValido() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        String token = jwtUtil.generarToken("admin@aura.com", "ADMIN");

        assertFalse(jwtUtil.validarToken(token));
        assertThrows(Exception.class, () -> jwtUtil.obtenerEmail(token));
    }

    @Test
    void firmaInvalida_noEsValido() {
        JwtUtil otro = new JwtUtil();
        ReflectionTestUtils.setField(otro, "secret", "OtraClaveSecretaMuyLargaParaHS256De32BytesMinimo");
        ReflectionTestUtils.setField(otro, "expiration", EXPIRATION);

        String token = otro.generarToken("admin@aura.com", "ADMIN");

        assertFalse(jwtUtil.validarToken(token));
        assertThrows(Exception.class, () -> jwtUtil.obtenerEmail(token));
    }

    @Test
    void tokenMalformado_noEsValido() {
        assertFalse(jwtUtil.validarToken("no-es-un-jwt"));
    }

    @Test
    void tokenNull_noEsValido() {
        assertFalse(jwtUtil.validarToken(null));
    }

    @Test
    void tokenVacio_noEsValido() {
        assertFalse(jwtUtil.validarToken(""));
    }

    @Test
    void tokenConEmailYRolCorrectos_claimsValidos() {
        String token = jwtUtil.generarToken("emp@aura.com", "EMPLEADO");

        assertEquals("emp@aura.com", jwtUtil.obtenerEmail(token));
        assertEquals("EMPLEADO", jwtUtil.obtenerRol(token));
    }
}
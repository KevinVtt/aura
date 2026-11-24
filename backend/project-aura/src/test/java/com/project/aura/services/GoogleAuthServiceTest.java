package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.project.aura.exception.ApiException;

class GoogleAuthServiceTest {

    @Test
    void verificar_sinClientId_devuelve503() {
        GoogleAuthService service = new GoogleAuthService();
        ReflectionTestUtils.setField(service, "clientId", "");

        ApiException ex = assertThrows(ApiException.class, () -> service.verificar("cualquiera"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void verificar_tokenIlegible_devuelve401() {
        GoogleAuthService service = new GoogleAuthService();
        ReflectionTestUtils.setField(service, "clientId", "client-id-de-prueba");

        ApiException ex = assertThrows(ApiException.class, () -> service.verificar("no-es-un-jwt"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}

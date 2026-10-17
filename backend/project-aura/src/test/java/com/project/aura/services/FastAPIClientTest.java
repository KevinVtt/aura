package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

class FastAPIClientTest {

    private MockRestServiceServer server;
    private FastAPIClient fastAPIClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        fastAPIClient = new FastAPIClient(restTemplate);
        ReflectionTestUtils.setField(fastAPIClient, "fastapiUrl", "http://localhost:8000");
    }

    @Test
    void chat_iaResponde502ConDetail_propagaDetalleComoBadGateway() {
        server.expect(requestTo("http://localhost:8000/chat"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .body("{\"detail\":\"x\"}")
                .contentType(MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> fastAPIClient.chat("sesion-1", "hola"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("x"));
    }

    @Test
    void chat_respuesta200SinBody_lanzaBadGatewaySinNpe() {
        server.expect(requestTo("http://localhost:8000/chat"))
            .andRespond(withSuccess());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> fastAPIClient.chat("sesion-1", "hola"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void chat_respuesta200SinCampoRespuesta_lanzaBadGateway() {
        server.expect(requestTo("http://localhost:8000/chat"))
            .andRespond(withSuccess("{\"otro\":\"valor\"}", MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> fastAPIClient.chat("sesion-1", "hola"));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void chat_respuesta200ConRespuesta_devuelveTexto() {
        server.expect(requestTo("http://localhost:8000/chat"))
            .andRespond(withSuccess("{\"respuesta\":\"hola mundo\"}", MediaType.APPLICATION_JSON));

        FastAPIClient.RespuestaIA resultado = fastAPIClient.chat("sesion-1", "hola");

        assertEquals("hola mundo", resultado.respuesta());
        assertTrue(resultado.fuentes().isEmpty());
    }

    @Test
    void chat_devuelveLasFuentesParaPoderMostrarlasEnElChat() {
        server.expect(requestTo("http://localhost:8000/chat"))
            .andRespond(withSuccess(
                "{\"respuesta\":\"hola\",\"fuentes\":[\"manual.pdf\",\"politicas.pdf\"]}",
                MediaType.APPLICATION_JSON));

        FastAPIClient.RespuestaIA resultado = fastAPIClient.chat("sesion-1", "hola");

        assertEquals(List.of("manual.pdf", "politicas.pdf"), resultado.fuentes());
    }
}

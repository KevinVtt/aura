package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FastAPIClient {

    private final RestTemplate restTemplate;

    @Value("${fastapi.url}")
    private String fastapiUrl;

    /** Lo que devuelve la IA: el texto y los documentos de los que salio. */
    public record RespuestaIA(String respuesta, List<String> fuentes) {
    }

    public RespuestaIA chat(String sesionId, String pregunta) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
            "sesion_id", sesionId,
            "pregunta",  pregunta
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(
                fastapiUrl + "/chat",
                request,
                Map.class
            );
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detalleDe(e));
        }

        Map<?, ?> cuerpo = response.getBody();
        if (cuerpo == null || cuerpo.get("respuesta") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "El servicio de IA no devolvió una respuesta válida");
        }

        List<String> fuentes = cuerpo.get("fuentes") instanceof List<?> lista
            ? lista.stream().map(String::valueOf).toList()
            : List.of();

        return new RespuestaIA((String) cuerpo.get("respuesta"), fuentes);
    }

    private String detalleDe(RestClientResponseException e) {
        try {
            Object detalle = new ObjectMapper()
                .readValue(e.getResponseBodyAsString(), Map.class).get("detail");
            if (detalle != null) {
                return detalle.toString();
            }
        } catch (Exception ignored) {
        }
        return e.getStatusText();
    }

    public void subirDocumento(String nombreArchivo, byte[] contenido) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(contenido) {
            @Override
            public String getFilename() { return nombreArchivo; }
        };
        body.add("archivo", resource);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(fastapiUrl + "/documentos/subir", request, Map.class);
    }

    public void limpiarHistorial(String sesionId) {
    restTemplate.delete(fastapiUrl + "/chat/historial/" + sesionId);
}

    public void eliminarDocumento(String nombreArchivo) {
        restTemplate.delete(fastapiUrl + "/documentos/" + nombreArchivo);
    }
}
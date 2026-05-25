package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FastAPIClient {

    private final RestTemplate restTemplate;

    @Value("${fastapi.url}")
    private String fastapiUrl;

    public String chat(String sesionId, String pregunta) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
            "sesion_id", sesionId,
            "pregunta",  pregunta
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            fastapiUrl + "/chat",
            request,
            Map.class
        );

        System.out.println("=== RESPUESTA FASTAPI ===");
        System.out.println(response.getBody());

        return (String) response.getBody().get("respuesta");
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
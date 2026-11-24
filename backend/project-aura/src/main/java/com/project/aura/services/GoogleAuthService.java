package com.project.aura.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.project.aura.exception.ApiException;

@Service
public class GoogleAuthService {

    // Client ID del OAuth de Google Cloud Console. Vacio = login con Google apagado.
    @Value("${aura.google.client-id:}")
    private String clientId;

    private GoogleIdTokenVerifier verifier;

    public GoogleIdToken.Payload verificar(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "El login con Google no está configurado en el servidor");
        }
        GoogleIdToken.Payload payload;
        try {
            GoogleIdToken token = verificador().verify(idToken);
            if (token == null) {
                throw new IllegalArgumentException("Token de Google nulo");
            }
            payload = token.getPayload();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token de Google inválido");
        }
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                "El correo de Google no está verificado");
        }
        return payload;
    }

    // Verifica firma, audiencia y expiracion contra las claves publicas de Google.
    private synchronized GoogleIdTokenVerifier verificador() {
        if (verifier == null) {
            verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
        }
        return verifier;
    }
}

package com.project.aura.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.project.aura.dto.GoogleLoginRequest;
import com.project.aura.dto.GoogleLoginResponse;
import com.project.aura.dto.LoginRequest;
import com.project.aura.dto.LoginResponse;
import com.project.aura.dto.SolicitudRequest;
import com.project.aura.exception.ApiException;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.EstadoUsuario;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.GoogleAuthService;
import com.project.aura.services.UsuarioService;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;
    private final GoogleAuthService googleAuthService;

    // El único login con contraseña que queda es el del super administrador.
    @Value("${aura.admin.email:admin@aura.com}")
    private String adminEmail;

    private final ConcurrentHashMap<String, LoginAttempt> intentos = new ConcurrentHashMap<>();

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 15 * 60 * 1000;

    // La ventana arranca en el último intento fallido, así las entradas viejas se pueden purgar.
    private record LoginAttempt(int count, Instant expira) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // Se normaliza igual que al crear usuarios: el login no debe fallar por
        // una mayuscula o un espacio de mas.
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!email.equalsIgnoreCase(adminEmail)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                "Ese acceso ya no usa contraseña: entrá con «Continuar con Google».");
        }

        // Purgar ventanas vencidas: la clave es la IP real, no un header falseable.
        Instant ahora = Instant.now();
        intentos.values().removeIf(a -> ahora.isAfter(a.expira()));

        // La clave suma el email: detras de un proxy todos comparten IP y cinco
        // fallos de una persona dejaban a la oficina entera sin poder entrar.
        String ip = httpRequest.getRemoteAddr() + "|" + email;
        LoginAttempt actual = intentos.get(ip);

        if (actual != null && actual.count() >= MAX_INTENTOS) {
            return ResponseEntity.status(429).build();
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    email,
                    request.getPassword()
                )
            );
        } catch (AuthenticationException e) {
            int nuevos = (actual == null ? 0 : actual.count()) + 1;
            intentos.put(ip, new LoginAttempt(nuevos, Instant.now().plusMillis(VENTANA_MS)));
            return ResponseEntity.status(401).build();
        }

        intentos.remove(ip);

        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElseThrow();

        String token = jwtUtil.generarToken(
            usuario.getContacto().getEmail(),
            usuario.getRol().name()
        );

        return ResponseEntity.ok(LoginResponse.builder()
            .token(token)
            .nombre(usuario.getNombre())
            .rol(usuario.getRol().name())
            .build());
    }

    @PostMapping("/google")
    public ResponseEntity<GoogleLoginResponse> google(
            @Valid @RequestBody GoogleLoginRequest request) {

        GoogleIdToken.Payload payload = googleAuthService.verificar(request.getIdToken());
        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByContactoEmail(email).orElse(null);

        if (usuario == null) {
            return ResponseEntity.ok(GoogleLoginResponse.builder()
                .email(email)
                .nombre((String) payload.get("name"))
                .estado("NO_REGISTRADO")
                .build());
        }
        // El estado null es una fila anterior a la aprobación: se trata como aprobada.
        if (usuario.getEstado() == EstadoUsuario.RECHAZADO) {
            return ResponseEntity.ok(estado(usuario, "RECHAZADO"));
        }
        if (usuario.getEstado() == EstadoUsuario.PENDIENTE) {
            return ResponseEntity.ok(estado(usuario, "PENDIENTE"));
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return ResponseEntity.ok(estado(usuario, "INACTIVO"));
        }

        String token = jwtUtil.generarToken(
            usuario.getContacto().getEmail(),
            usuario.getRol().name()
        );
        return ResponseEntity.ok(GoogleLoginResponse.builder()
            .token(token)
            .nombre(usuario.getNombre())
            .email(email)
            .rol(usuario.getRol().name())
            .estado("APROBADO")
            .build());
    }

    @PostMapping("/solicitud")
    public ResponseEntity<Void> solicitud(
            @Valid @RequestBody SolicitudRequest request) {

        GoogleIdToken.Payload payload = googleAuthService.verificar(request.getIdToken());
        usuarioService.registrarSolicitud(
            (String) payload.get("name"),
            payload.getEmail(),
            request.getTelefono());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private GoogleLoginResponse estado(Usuario usuario, String estado) {
        return GoogleLoginResponse.builder()
            .nombre(usuario.getNombre())
            .email(usuario.getContacto().getEmail())
            .rol(usuario.getRol().name())
            .estado(estado)
            .build();
    }

}

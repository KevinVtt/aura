package com.project.aura.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import com.project.aura.dto.LoginRequest;
import com.project.aura.dto.LoginResponse;
import com.project.aura.jwt.JwtUtil;
import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.repositories.UsuarioRepository;
import com.project.aura.services.UsuarioService;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    private final ConcurrentHashMap<String, LoginAttempt> intentos = new ConcurrentHashMap<>();

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 15 * 60 * 1000;

    // La ventana arranca en el último intento fallido, así las entradas viejas se pueden purgar.
    private record LoginAttempt(int count, Instant expira) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // Purgar ventanas vencidas: la clave es la IP real, no un header falseable.
        Instant ahora = Instant.now();
        intentos.values().removeIf(a -> ahora.isAfter(a.expira()));

        String ip = httpRequest.getRemoteAddr();
        LoginAttempt actual = intentos.get(ip);

        if (actual != null && actual.count() >= MAX_INTENTOS) {
            return ResponseEntity.status(429).build();
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
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
            .findByContactoEmail(request.getEmail())
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

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(
        @Valid @RequestBody Usuario request) {

        Contacto contacto = Contacto.builder()
            .email(request.getContacto().getEmail())
            .telefono(request.getContacto().getTelefono())
            .build();

        Usuario usuario = Usuario.builder()
            .nombre(request.getNombre())
            .password(request.getPassword())
            .rol(request.getRol())
            .contacto(contacto)
            .build();

        usuarioService.crear(usuario);

        String token = jwtUtil.generarToken(
            contacto.getEmail(),
            usuario.getRol().name()
        );

        return ResponseEntity.ok(LoginResponse.builder()
            .token(token)
            .nombre(usuario.getNombre())
            .rol(usuario.getRol().name())
            .build());
    }
}

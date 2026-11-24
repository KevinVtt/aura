package com.project.aura.jwt;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

import com.project.aura.models.Usuario;
import com.project.aura.repositories.UsuarioRepository;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;

      @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                autenticar(header.substring(7), request);
            } catch (Exception e) {
                // Token roto o usuario inexistente: sigue sin autenticar.
                SecurityContextHolder.clearContext();
            }
        }

        // El chain se llama una sola vez y fuera del try: si lanza (el cliente
        // corta una subida, por ejemplo), no se reprocesa el pedido.
        chain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request) {
        if (!jwtUtil.validarToken(token)) {
            return;
        }

        String email = jwtUtil.obtenerEmail(token);

        // El rol se toma de la base de datos, nunca del claim del token.
        // Un usuario inexistente o inactivo no queda autenticado.
        Usuario usuario = usuarioRepository
            .findByContactoEmail(email)
            .orElse(null);

        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            return;
        }

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
            );

        auth.setDetails(new WebAuthenticationDetailsSource()
            .buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
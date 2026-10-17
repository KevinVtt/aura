package com.project.aura.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UsuarioRepository usuarioRepository;

    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtFilter(jwtUtil, usuarioRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario(Rol rol, boolean activo) {
        return Usuario.builder()
            .id(1L)
            .nombre("Usuario")
            .rol(rol)
            .activo(activo)
            .contacto(Contacto.builder().email("user@aura.com").build())
            .build();
    }

    private MockHttpServletRequest requestConToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    @Test
    void tokenValido_usaElRolDeLaBaseYNoElDelClaim() throws Exception {
        when(jwtUtil.validarToken("token")).thenReturn(true);
        when(jwtUtil.obtenerEmail("token")).thenReturn("user@aura.com");
        when(usuarioRepository.findByContactoEmail("user@aura.com"))
            .thenReturn(Optional.of(usuario(Rol.EMPLEADO, true)));

        filter.doFilter(requestConToken(), new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("ROLE_EMPLEADO", auth.getAuthorities().iterator().next().getAuthority());
        verify(jwtUtil, never()).obtenerRol(any());
    }

    @Test
    void usuarioInactivo_noQuedaAutenticado() throws Exception {
        when(jwtUtil.validarToken("token")).thenReturn(true);
        when(jwtUtil.obtenerEmail("token")).thenReturn("user@aura.com");
        when(usuarioRepository.findByContactoEmail("user@aura.com"))
            .thenReturn(Optional.of(usuario(Rol.ADMIN, false)));

        filter.doFilter(requestConToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void usuarioInexistente_noQuedaAutenticado() throws Exception {
        when(jwtUtil.validarToken("token")).thenReturn(true);
        when(jwtUtil.obtenerEmail("token")).thenReturn("user@aura.com");
        when(usuarioRepository.findByContactoEmail("user@aura.com")).thenReturn(Optional.empty());

        filter.doFilter(requestConToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenInvalido_noQuedaAutenticado() throws Exception {
        when(jwtUtil.validarToken("token")).thenReturn(false);

        filter.doFilter(requestConToken(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sinHeader_noAutentica() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

package com.project.aura.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // El email del super admin llega por @Value; en un test unitario hay
        // que inyectarlo a mano.
        ReflectionTestUtils.setField(usuarioService, "adminEmail", "admin@aura.com");
        Contacto contacto = Contacto.builder().email("juan@aura.com").build();
        usuario = Usuario.builder()
            .id(1L)
            .nombre("Juan")
            .password("password123")
            .rol(Rol.EMPLEADO)
            .activo(true)
            .contacto(contacto)
            .build();
    }

    @Test
    void crear_guardaUsuarioConPasswordEncodeadoYActivo() {
        when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = usuarioService.crear(usuario);

        assertEquals("$2a$10$encoded", resultado.getPassword());
        assertTrue(resultado.getActivo());
        verify(passwordEncoder).encode("password123");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void crear_emailDuplicado_lanzaExcepcion() {
        when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.of(usuario));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> usuarioService.crear(usuario));

        assertEquals("El email ya está registrado", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void desactivar_marcaUsuarioInactivo() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = usuarioService.desactivar(1L);

        assertFalse(resultado.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void activar_marcaUsuarioActivo() {
        usuario.setActivo(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = usuarioService.activar(1L);

        assertTrue(resultado.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void desactivar_idInexistente_lanzaExcepcion() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> usuarioService.desactivar(999L));

        assertEquals("Usuario no encontrado", ex.getMessage());
    }

    @Test
    void activar_idInexistente_lanzaExcepcion() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> usuarioService.activar(999L));
    }

    @Test
    void buscarPorId_devuelveUsuario() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        Usuario resultado = usuarioService.buscarPorId(1L);

        assertEquals("Juan", resultado.getNombre());
    }

    @Test
    void buscarPorId_idInexistente_lanzaExcepcion() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> usuarioService.buscarPorId(999L));
    }

    @Test
    void buscarPorEmail_devuelveUsuario() {
        when(usuarioRepository.findByContactoEmail("juan@aura.com")).thenReturn(Optional.of(usuario));

        Usuario resultado = usuarioService.buscarPorEmail("juan@aura.com");

        assertEquals("Juan", resultado.getNombre());
    }

    @Test
    void buscarPorEmail_emailInexistente_lanzaExcepcion() {
        when(usuarioRepository.findByContactoEmail("no@existe.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> usuarioService.buscarPorEmail("no@existe.com"));
    }

    @Test
    void listarTodos_devuelveLista() {
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario));

        List<Usuario> resultado = usuarioService.listarTodos();

        assertEquals(1, resultado.size());
    }

    @Test
    void listarTodos_sinUsuarios_devuelveListaVacia() {
        when(usuarioRepository.findAll()).thenReturn(List.of());

        List<Usuario> resultado = usuarioService.listarTodos();

        assertTrue(resultado.isEmpty());
    }

    // ── Super administrador ──────────────────────────────────────────────────

    private Usuario superAdmin() {
        return Usuario.builder()
            .id(9L)
            .nombre("Admin")
            .rol(Rol.ADMIN)
            .activo(true)
            .contacto(Contacto.builder().email("admin@aura.com").build())
            .build();
    }

    @Test
    void cambiarRol_cambiaElRolDelUsuario() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.cambiarRol(1L, Rol.ADMIN);

        assertEquals(Rol.ADMIN, usuario.getRol());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void cambiarRol_idInexistente_lanzaExcepcion() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> usuarioService.cambiarRol(999L, Rol.ADMIN));
    }

    @Test
    void cambiarRol_sobreElSuperAdmin_lanzaExcepcion() {
        Usuario admin = superAdmin();
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(admin));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> usuarioService.cambiarRol(9L, Rol.EMPLEADO));

        assertTrue(ex.getMessage().contains("super administrador"));
        assertEquals(Rol.ADMIN, admin.getRol());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void desactivar_sobreElSuperAdmin_lanzaExcepcion() {
        Usuario admin = superAdmin();
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(admin));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> usuarioService.desactivar(9L));

        assertTrue(ex.getMessage().contains("super administrador"));
        assertTrue(admin.getActivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void activar_sobreElSuperAdmin_estaPermitido() {
        // Si quedo inactivo por un cambio manual en la base, hay que poder
        // recuperarlo desde el panel.
        Usuario admin = superAdmin();
        admin.setActivo(false);
        when(usuarioRepository.findById(9L)).thenReturn(Optional.of(admin));

        usuarioService.activar(9L);

        assertTrue(admin.getActivo());
        verify(usuarioRepository).save(admin);
    }

    @Test
    void listarTodos_marcaAlSuperAdmin() {
        when(usuarioRepository.findAll()).thenReturn(List.of(usuario, superAdmin()));

        List<Usuario> resultado = usuarioService.listarTodos();

        assertFalse(resultado.get(0).getEsSuperAdmin());
        assertTrue(resultado.get(1).getEsSuperAdmin());
    }
}
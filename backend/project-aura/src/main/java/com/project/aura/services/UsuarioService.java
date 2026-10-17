package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.aura.exception.ApiException;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // El email del super administrador: no puede cambiar de rol ni desactivarse.
    @Value("${aura.admin.email:admin@aura.com}")
    private String adminEmail;

    public List<Usuario> listarTodos() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        for (Usuario usuario : usuarios) {
            usuario.setEsSuperAdmin(esSuperAdmin(usuario));
        }
        return usuarios;
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByContactoEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public Usuario crear(Usuario usuario) {
        if (usuarioRepository.findByContactoEmail(usuario.getContacto().getEmail()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    public Usuario desactivar(Long id) {
        Usuario usuario = buscarPorId(id);
        if (esSuperAdmin(usuario)) {
            throw new ApiException(HttpStatus.CONFLICT,
                "El super administrador no se puede desactivar");
        }
        usuario.setActivo(false);
        return usuarioRepository.save(usuario);
    }

    public Usuario activar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    public void cambiarRol(Long id, Rol rol) {
        Usuario usuario = buscarPorId(id);
        if (esSuperAdmin(usuario)) {
            throw new ApiException(HttpStatus.CONFLICT,
                "El super administrador no puede cambiar de rol");
        }
        usuario.setRol(rol);
        usuarioRepository.save(usuario);
    }

    private boolean esSuperAdmin(Usuario usuario) {
        return usuario.getContacto() != null
            && usuario.getContacto().getEmail() != null
            && usuario.getContacto().getEmail().equalsIgnoreCase(adminEmail);
    }
}
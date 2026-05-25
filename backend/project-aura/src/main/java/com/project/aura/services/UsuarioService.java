package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.aura.models.Usuario;
import com.project.aura.repositories.ContactoRepository;
import com.project.aura.repositories.UsuarioRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByContactoEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
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
        usuario.setActivo(false);
        return usuarioRepository.save(usuario);
    }

    public Usuario activar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }
}
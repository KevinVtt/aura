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
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final SecureRandom RANDOM = new SecureRandom();
    // Sin caracteres ambiguos (0/O, 1/l/I) para que sea fácil de copiar del mail.
    private static final String[] CONJUNTOS = {
        "ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%&*?-_"
    };

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    // El email del super administrador: no puede cambiar de rol ni desactivarse.
    @Value("${aura.admin.email:admin@aura.com}")
    private String adminEmail;

    // Lista separada por comas (ALLOWED_EMAIL_DOMAINS). Vacia = cualquier dominio.
    @Value("${aura.allowed-email-domains:}")
    private List<String> dominiosPermitidos;

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

    @Transactional
    public Usuario crear(Usuario usuario) {
        String email = usuario.getContacto().getEmail();
        if (!dominiosPermitidos.isEmpty()
                && !dominiosPermitidos.contains(email.substring(email.indexOf('@') + 1).toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Solo se permiten correos de los dominios: " + String.join(", ", dominiosPermitidos));
        }
        if (usuarioRepository.findByContactoEmail(email).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }
        String clave = usuario.getPassword() == null || usuario.getPassword().isBlank()
            ? generarPassword() : usuario.getPassword();
        usuario.setPassword(passwordEncoder.encode(clave));
        usuario.setActivo(true);
        usuario.setMustChangePassword(true);
        Usuario guardado = usuarioRepository.save(usuario);
        mailService.enviarAlta(email, usuario.getNombre(), clave);
        return guardado;
    }

    @Transactional
    public Usuario reenviarAlta(Long id) {
        Usuario usuario = buscarPorId(id);
        String clave = generarPassword();
        usuario.setPassword(passwordEncoder.encode(clave));
        usuario.setMustChangePassword(true);
        Usuario guardado = usuarioRepository.save(usuario);
        mailService.enviarAlta(usuario.getContacto().getEmail(), usuario.getNombre(), clave);
        return guardado;
    }

    @Transactional
    public void cambiarPassword(String email, String actual, String nueva) {
        Usuario usuario = buscarPorEmail(email);
        if (!passwordEncoder.matches(actual, usuario.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        usuario.setPassword(passwordEncoder.encode(nueva));
        usuario.setMustChangePassword(false);
        usuarioRepository.save(usuario);
    }

    private String generarPassword() {
        List<Character> clave = new ArrayList<>();
        for (String conjunto : CONJUNTOS) {
            clave.add(conjunto.charAt(RANDOM.nextInt(conjunto.length())));
        }
        String todos = String.join("", CONJUNTOS);
        while (clave.size() < 14) {
            clave.add(todos.charAt(RANDOM.nextInt(todos.length())));
        }
        Collections.shuffle(clave, RANDOM);
        return clave.stream().map(String::valueOf).collect(Collectors.joining());
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
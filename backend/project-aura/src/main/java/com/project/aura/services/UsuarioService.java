package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.project.aura.exception.ApiException;
import com.project.aura.models.Contacto;
import com.project.aura.models.Usuario;
import com.project.aura.models.enums.EstadoUsuario;
import com.project.aura.models.enums.Rol;
import com.project.aura.repositories.UsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
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

    public List<Usuario> listarPendientes() {
        return usuarioRepository.findByEstadoOrderByNombreAsc(EstadoUsuario.PENDIENTE);
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByContactoEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    // Alta manual desde el panel: queda aprobado y entra directo con Google.
    @Transactional
    public Usuario crear(Usuario usuario) {
        String email = normalizarEmail(usuario.getContacto().getEmail());
        usuario.getContacto().setEmail(email);
        if (usuarioRepository.findByContactoEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        usuario.setPassword(null);
        usuario.setActivo(true);
        usuario.setEstado(EstadoUsuario.APROBADO);
        Usuario guardado = usuarioRepository.save(usuario);
        despuesDelCommit(() -> mailService.enviarAprobado(email, usuario.getNombre()));
        return guardado;
    }

    // Primer ingreso con Google: queda pendiente hasta que un admin lo apruebe.
    @Transactional
    public Usuario registrarSolicitud(String nombre, String email, String telefono) {
        String correo = normalizarEmail(email);
        Usuario existente = usuarioRepository.findByContactoEmail(correo).orElse(null);
        if (existente != null) {
            // Reintentar desde la pantalla de pendiente no duplica la solicitud.
            if (existente.getEstado() == EstadoUsuario.PENDIENTE) {
                return existente;
            }
            throw new ApiException(HttpStatus.CONFLICT, "Ese correo ya tiene una cuenta en Aura");
        }
        // El nombre viene de Google: puede faltar, ser una sola letra o traer
        // mas de lo que admite la columna.
        String nombreSolicitud = nombre == null ? "" : nombre.trim();
        if (nombreSolicitud.length() < 2) {
            nombreSolicitud = correo;
        }
        if (nombreSolicitud.length() > 100) {
            nombreSolicitud = nombreSolicitud.substring(0, 100);
        }
        Usuario solicitud = Usuario.builder()
            .nombre(nombreSolicitud)
            .rol(Rol.EMPLEADO)
            .activo(false)
            .estado(EstadoUsuario.PENDIENTE)
            .contacto(Contacto.builder().email(correo).telefono(telefono).build())
            .build();
        return usuarioRepository.save(solicitud);
    }

    @Transactional
    public Usuario aprobar(Long id) {
        Usuario usuario = buscarPorId(id);
        if (esSuperAdmin(usuario)) {
            throw new ApiException(HttpStatus.CONFLICT,
                "El super administrador no se puede aprobar ni rechazar");
        }
        usuario.setEstado(EstadoUsuario.APROBADO);
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.save(usuario);
        despuesDelCommit(() -> mailService.enviarAprobado(
            usuario.getContacto().getEmail(), usuario.getNombre()));
        return guardado;
    }

    @Transactional
    public Usuario rechazar(Long id) {
        Usuario usuario = buscarPorId(id);
        if (esSuperAdmin(usuario)) {
            throw new ApiException(HttpStatus.CONFLICT,
                "El super administrador no se puede aprobar ni rechazar");
        }
        usuario.setEstado(EstadoUsuario.RECHAZADO);
        usuario.setActivo(false);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario reenviarAviso(Long id) {
        Usuario usuario = buscarPorId(id);
        // El mail dice que el acceso fue aprobado: no se manda a quien no lo esta.
        if (usuario.getEstado() != null && usuario.getEstado() != EstadoUsuario.APROBADO) {
            throw new ApiException(HttpStatus.CONFLICT,
                "Todavía no está aprobado: aprobalo desde Solicitudes");
        }
        despuesDelCommit(() -> mailService.enviarAprobado(
            usuario.getContacto().getEmail(), usuario.getNombre()));
        return usuario;
    }

    /** Envia el correo recien cuando el cambio quedo guardado: si el commit
     *  falla, nadie recibe un aviso que no corresponde. */
    private void despuesDelCommit(Runnable envio) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }

    // Se guarda normalizado para que "Juan@Empresa.com " y "juan@empresa.com"
    // sean el mismo usuario y el login no dependa de mayusculas ni espacios.
    private String normalizarEmail(String email) {
        String correo = email.trim().toLowerCase(Locale.ROOT);
        if (!dominiosPermitidos.isEmpty()
                && !dominiosPermitidos.contains(correo.substring(correo.indexOf('@') + 1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Solo se permiten correos de los dominios: " + String.join(", ", dominiosPermitidos));
        }
        return correo;
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
        // Reactivar tambien aprueba: si no, el panel lo muestra activo y Google
        // le sigue negando la entrada.
        if (usuario.getEstado() != null && usuario.getEstado() != EstadoUsuario.APROBADO) {
            usuario.setEstado(EstadoUsuario.APROBADO);
        }
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

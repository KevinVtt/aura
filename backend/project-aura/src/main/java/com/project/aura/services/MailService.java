package com.project.aura.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.project.aura.exception.ApiException;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${aura.mail.from}")
    private String from;

    @Value("${aura.login-url}")
    private String loginUrl;

    public void enviarAlta(String destino, String nombre, String clave) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom("Aura <" + from + ">");
        mensaje.setTo(destino);
        mensaje.setSubject("Tu acceso a Aura");
        mensaje.setText(
            "Hola " + nombre + ",\n\n" +
            "Se creó tu cuenta en Aura.\n\n" +
            "Usuario: " + destino + "\n" +
            "Contraseña temporal: " + clave + "\n\n" +
            "Ingresá en " + loginUrl + " y cambiá la contraseña al entrar por primera vez.\n");
        try {
            mailSender.send(mensaje);
        } catch (MailException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                "No se pudo enviar el correo. No se guardaron los cambios.");
        }
    }
}

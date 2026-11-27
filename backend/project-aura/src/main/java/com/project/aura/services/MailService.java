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

    public void enviarAprobado(String destino, String nombre) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom("Aura <" + from + ">");
        mensaje.setTo(destino);
        mensaje.setSubject("Tu acceso a Aura fue aprobado");
        mensaje.setText(
            "Hola " + nombre + ",\n\n" +
            "Tu acceso a Aura ya está aprobado.\n\n" +
            "Entrá en " + loginUrl + " con «Continuar con Google» usando esta misma cuenta: " + destino + "\n");
        try {
            mailSender.send(mensaje);
        } catch (MailException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                "No se pudo enviar el correo. Usá «Reenviar aviso» para intentar de nuevo.");
        }
    }
}

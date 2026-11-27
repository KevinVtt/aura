package com.project.aura.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Siempre 200: el frontend decide qué pantalla mostrar con "estado".
// El token solo viene cuando el estado es APROBADO.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginResponse {

    private String token;
    private String nombre;
    private String email;
    private String rol;
    private String estado;
}

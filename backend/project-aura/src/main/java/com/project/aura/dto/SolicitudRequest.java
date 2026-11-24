package com.project.aura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRequest {

    @NotBlank(message = "El ID token es obligatorio")
    private String idToken;

    @Pattern(regexp = "^$|^[0-9]{10,15}$", message = "El teléfono debe tener entre 10 y 15 números")
    private String telefono;
}

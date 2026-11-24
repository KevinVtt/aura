package com.project.aura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambioPasswordRequest {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @NotBlank(message = "La contraseña nueva es obligatoria")
    private String passwordNueva;
}

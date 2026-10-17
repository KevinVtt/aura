package com.project.aura.dto;

import com.project.aura.models.enums.Rol;
import jakarta.validation.constraints.NotNull;

public class CambioRolRequest {

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
}

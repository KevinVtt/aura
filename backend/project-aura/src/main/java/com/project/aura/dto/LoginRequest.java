package com.project.aura.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    // @Email acepta "user@servidor" sin punto en el dominio; el login exige lo mismo
    // que el alta de usuarios.
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
             message = "El email no tiene un formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
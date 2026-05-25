package com.project.aura.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.aura.models.enums.Origen;

@Entity
@Table(name = "mensajes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "conversacion_id", nullable = false)
    @JsonIgnore
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "La pregunta no puede estar vacía")
    private String contenido;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    
}
package com.project.aura.dto;

import jakarta.validation.constraints.NotBlank;

public class PreguntaRequest {
    @NotBlank(message = "La pregunta no puede estar vacía")
    private String pregunta;
    
    // getter y setter
    public String getPregunta() { return pregunta; }
    public void setPregunta(String pregunta) { this.pregunta = pregunta; }
}
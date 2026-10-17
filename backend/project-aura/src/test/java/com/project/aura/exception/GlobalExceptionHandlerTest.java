package com.project.aura.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validacionFallida_devuelve400ConMapaCampoError() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "El email es obligatorio"));
        bindingResult.addError(new FieldError("request", "password", "La contraseña es obligatoria"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationErrors(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El email es obligatorio", response.getBody().get("email"));
        assertEquals("La contraseña es obligatoria", response.getBody().get("password"));
    }

    @Test
    void validacionFallida_conUnSoloError_devuelveMapaConUnCampo() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "pregunta", "La pregunta no puede estar vacía"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationErrors(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("La pregunta no puede estar vacía", response.getBody().get("pregunta"));
    }

    @Test
    void validacionFallida_campoAnidado_usaElNombreHoja() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getMethod("dummy", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "contacto.email", "El email no tiene un formato válido"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationErrors(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El email no tiene un formato válido", response.getBody().get("email"));
    }

    @Test
    void runtimeException_devuelve400ConError() {
        ResponseEntity<Map<String, String>> response =
            handler.handleRuntimeException(new RuntimeException("El email ya está registrado"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("El email ya está registrado", response.getBody().get("error"));
    }

    @Test
    void apiException_devuelveElStatusIndicadoConElMensaje() {
        ResponseEntity<Map<String, String>> response =
            handler.handleApiException(new ApiException(HttpStatus.NOT_FOUND, "Conversación no encontrada"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Conversación no encontrada", response.getBody().get("error"));
    }

    @Test
    void exceptionGenerica_devuelve500ConMensajeGenerico() {
        ResponseEntity<Map<String, String>> response =
            handler.handleException(new Exception("detalle-interno-secreto"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Error interno del servidor", response.getBody().get("error"));
    }

    @Test
    void exceptionGenerica_noExponeDetalleNiStackTrace() {
        ResponseEntity<Map<String, String>> response =
            handler.handleException(new Exception("detalle-interno-secreto"));

        assertFalse(response.getBody().containsKey("stackTrace"));
        assertFalse(response.getBody().toString().contains("detalle-interno-secreto"));
    }

    public void dummy(String arg) {
    }
}
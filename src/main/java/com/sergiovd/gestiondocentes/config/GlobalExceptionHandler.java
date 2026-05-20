package com.sergiovd.gestiondocentes.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones de la aplicación.
 * Captura excepciones no controladas en los controladores y redirige
 * a las vistas de error correspondientes, proporcionando mensajes
 * claros al usuario y registrando los detalles técnicos en el log.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Captura excepciones de tamaño de archivo excedido en las subidas de material.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex, Model model) {
        log.warn("Intento de subida de archivo que excede el tamaño máximo: {}", ex.getMessage());
        model.addAttribute("error", "El archivo supera el tamaño máximo permitido (10 MB).");
        return "error/500";
    }

    /**
     * Captura los 404 de recursos estáticos (imágenes, css, js no encontrados)
     * y devuelve la página de error 404 sin contaminar los logs con stack traces de 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(NoResourceFoundException ex) {
        log.debug("Recurso estático no encontrado: {}", ex.getResourcePath());
        return "error/404";
    }

    /**
     * Captura los errores de validación @Valid en endpoints REST y devuelve un JSON
     * con el detalle de cada campo erróneo. Mejora la experiencia de los consumidores de la API.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errores.put(err.getField(), err.getDefaultMessage()));
        log.warn("Errores de validación en API REST: {}", errores);
        return ResponseEntity.badRequest().body(errores);
    }

    /**
     * Captura cualquier excepción no controlada para evitar que el usuario
     * vea un stack trace y garantizar una respuesta limpia.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Excepción no controlada: {}", ex.getMessage(), ex);
        model.addAttribute("error", "Se ha producido un error inesperado. Por favor, inténtelo de nuevo.");
        return "error/500";
    }
}

package com.sergiovd.gestiondocentes.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import java.time.LocalDate;

@Data
public class SolicitudDTO {

    @NotNull(message = "El identificador del docente es obligatorio")
    private Long idDocente;

    private String nombreDocente;

    @NotNull(message = "El día solicitado es obligatorio")
    @FutureOrPresent(message = "El día solicitado no puede estar en el pasado")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate diaSolicitado;

    @Size(max = 500, message = "La descripción no puede exceder los 500 caracteres")
    private String descripcion;
}
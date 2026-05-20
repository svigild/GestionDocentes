package com.sergiovd.gestiondocentes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Entity
public class Docente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(min = 2, max = 150, message = "Los apellidos deben tener entre 2 y 150 caracteres")
    private String apellidos;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotBlank(message = "Las siglas son obligatorias")
    @Size(min = 2, max = 5, message = "Las siglas deben tener entre 2 y 5 caracteres")
    private String siglas;

    // 1=Carrera, 2=Prácticas, 3=Interino
    @Min(value = 1, message = "El tipo de funcionario debe ser 1, 2 o 3")
    @Max(value = 3, message = "El tipo de funcionario debe ser 1, 2 o 3")
    private Integer tipoFuncionario;

    private LocalDate fechaAntiguedad;

    @JsonIgnore
    private String password;

    @DecimalMin(value = "0.0", message = "La nota no puede ser negativa")
    @DecimalMax(value = "10.0", message = "La nota máxima es 10")
    private Double notaOposicion;

    // Para indicar si se ha cambiado la contraseña inicial o no, lo cual es obligatorio
    private Boolean passwordChanged = false;

    @ManyToOne
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    @ManyToOne
    @JoinColumn(name = "rol_id")
    private Rol rol;

    @OneToMany(mappedBy = "docente")
    @JsonIgnore
    private List<AsuntoPropio> asuntosPropios;

    @OneToMany(mappedBy = "docente")
    @JsonIgnore
    private List<Horario> horarios;

    private Integer guardiasRealizadas = 0;
}

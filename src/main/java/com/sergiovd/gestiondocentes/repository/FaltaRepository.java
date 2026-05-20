package com.sergiovd.gestiondocentes.repository;

import com.sergiovd.gestiondocentes.model.Falta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FaltaRepository extends JpaRepository<Falta, Long> {

    // Buscar faltas por fecha
    List<Falta> findByFecha(LocalDate fecha);

    // Buscar faltas de un docente concreto (a través de horario -> docente)
    @Query("SELECT f FROM Falta f WHERE f.horario.docente.id = :docenteId ORDER BY f.fecha DESC")
    List<Falta> findByDocenteId(@Param("docenteId") Long docenteId);

    // Buscar faltas de un docente en una fecha concreta
    @Query("SELECT f FROM Falta f WHERE f.horario.docente.id = :docenteId AND f.fecha = :fecha")
    List<Falta> findByDocenteIdAndFecha(@Param("docenteId") Long docenteId, @Param("fecha") LocalDate fecha);
}

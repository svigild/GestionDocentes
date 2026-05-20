package com.sergiovd.gestiondocentes.repository;

import com.sergiovd.gestiondocentes.model.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HorarioRepository extends JpaRepository<Horario, Long> {
    List<Horario> findByDocenteId(Long docenteId);
    void deleteByDocenteId(Long docenteId);

    // Buscar horarios por día y hora (para el cuadrante diario de guardias)
    List<Horario> findByDiaAndHora(Integer dia, Integer hora);

    // Buscar todos los docentes que imparten clase en un ciclo concreto
    @Query("SELECT DISTINCT h.docente.id FROM Horario h WHERE h.asignatura.ciclo.id = :cicloId")
    List<Long> findDocenteIdsByCicloId(@Param("cicloId") Long cicloId);
}

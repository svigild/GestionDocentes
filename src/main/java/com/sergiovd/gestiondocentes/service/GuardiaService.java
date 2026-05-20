package com.sergiovd.gestiondocentes.service;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.Horario;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.HorarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GuardiaService {

    @Autowired
    private DocenteRepository docenteRepo;

    @Autowired
    private HorarioRepository horarioRepo;

    /**
     * Algoritmo principal de asignación de guardias según el enunciado (3 pasos):
     *
     * PASO 1: Mismo departamento que el ausente → el que lleve menos guardias.
     * PASO 2: Da clase en el mismo grupo/ciclo que el ausente → el que lleve menos guardias.
     * PASO 3: Cualquier docente del centro → el que lleve menos guardias.
     */
    public Docente asignarGuardia(Long idDocenteAusente) {
        Docente ausente = docenteRepo.findById(idDocenteAusente).orElse(null);
        if (ausente == null) return null;

        List<Docente> todos = docenteRepo.findAll();
        // Quito al docente ausente de la lista de candidatos
        todos.removeIf(d -> d.getId().equals(idDocenteAusente));

        // ============================================================
        // PASO 1: Mismo departamento, ordenado por menos guardias
        // ============================================================
        if (ausente.getDepartamento() != null) {
            List<Docente> mismoDepto = todos.stream()
                    .filter(d -> d.getDepartamento() != null
                            && d.getDepartamento().getId().equals(ausente.getDepartamento().getId()))
                    .sorted(Comparator.comparingInt(this::getGuardiasRealizadasSafe))
                    .collect(Collectors.toList());

            if (!mismoDepto.isEmpty()) return mismoDepto.get(0);
        }

        // ============================================================
        // PASO 2: Da clase en el mismo grupo/ciclo que el ausente
        // Busco los ciclos en los que imparte el ausente y luego busco
        // otros docentes que compartan al menos un ciclo.
        // ============================================================
        List<Horario> horariosAusente = horarioRepo.findByDocenteId(idDocenteAusente);
        Set<Long> ciclosAusente = horariosAusente.stream()
                .filter(h -> h.getAsignatura() != null && h.getAsignatura().getCiclo() != null)
                .map(h -> h.getAsignatura().getCiclo().getId())
                .collect(Collectors.toSet());

        if (!ciclosAusente.isEmpty()) {
            // Reúno todos los IDs de docentes que imparten clase en esos ciclos
            Set<Long> docentesMismoGrupo = new HashSet<>();
            for (Long cicloId : ciclosAusente) {
                docentesMismoGrupo.addAll(horarioRepo.findDocenteIdsByCicloId(cicloId));
            }
            // Quito al propio ausente
            docentesMismoGrupo.remove(idDocenteAusente);

            List<Docente> candidatosMismoGrupo = todos.stream()
                    .filter(d -> docentesMismoGrupo.contains(d.getId()))
                    .sorted(Comparator.comparingInt(this::getGuardiasRealizadasSafe))
                    .collect(Collectors.toList());

            if (!candidatosMismoGrupo.isEmpty()) return candidatosMismoGrupo.get(0);
        }

        // ============================================================
        // PASO 3: Cualquier docente, el que lleve menos guardias
        // ============================================================
        todos.sort(Comparator.comparingInt(this::getGuardiasRealizadasSafe));
        return todos.isEmpty() ? null : todos.get(0);
    }

    // Helper para evitar NullPointerExceptions si el contador de guardias no se inicializó a 0
    private int getGuardiasRealizadasSafe(Docente d) {
        return d.getGuardiasRealizadas() == null ? 0 : d.getGuardiasRealizadas();
    }

    /**
     * Método auxiliar que devuelve una descripción del criterio por el cual se asignó la guardia.
     * Útil para mostrar al usuario por qué se eligió a este sustituto.
     */
    public String obtenerCriterioAsignacion(Long idDocenteAusente, Docente sustituto) {
        if (sustituto == null) return "No se encontró sustituto";

        Docente ausente = docenteRepo.findById(idDocenteAusente).orElse(null);
        if (ausente == null) return "Desconocido";

        // Comprobar si es del mismo departamento
        if (ausente.getDepartamento() != null && sustituto.getDepartamento() != null
                && ausente.getDepartamento().getId().equals(sustituto.getDepartamento().getId())) {
            return "Mismo departamento (" + ausente.getDepartamento().getNombre() + ")";
        }

        // Comprobar si comparten ciclo/grupo
        List<Horario> horariosAusente = horarioRepo.findByDocenteId(idDocenteAusente);
        Set<Long> ciclosAusente = horariosAusente.stream()
                .filter(h -> h.getAsignatura() != null && h.getAsignatura().getCiclo() != null)
                .map(h -> h.getAsignatura().getCiclo().getId())
                .collect(Collectors.toSet());

        List<Horario> horariosSustituto = horarioRepo.findByDocenteId(sustituto.getId());
        for (Horario h : horariosSustituto) {
            if (h.getAsignatura() != null && h.getAsignatura().getCiclo() != null
                    && ciclosAusente.contains(h.getAsignatura().getCiclo().getId())) {
                return "Mismo grupo/ciclo (" + h.getAsignatura().getCiclo().getCodigo() + ")";
            }
        }

        return "Menor carga de guardias (criterio general)";
    }
}
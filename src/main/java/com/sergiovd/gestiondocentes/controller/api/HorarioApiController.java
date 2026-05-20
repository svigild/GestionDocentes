package com.sergiovd.gestiondocentes.controller.api;

import com.sergiovd.gestiondocentes.model.Horario;
import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.HorarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/horarios")
@Tag(name = "Horarios", description = "Operaciones relacionadas con los horarios de los docentes")
public class HorarioApiController {

    @Autowired
    private DocenteRepository docenteRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @GetMapping
    @Operation(summary = "Listar todos los horarios", description = "Devuelve la lista completa de franjas horarias del sistema")
    @ApiResponse(responseCode = "200", description = "Lista de horarios obtenida correctamente")
    public List<Horario> listarHorarios() {
        return horarioRepository.findAll();
    }

    @GetMapping("/docente/{docenteId}")
    @Operation(summary = "Obtener horario de un docente", description = "Devuelve el horario semanal completo de un docente específico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horario obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Docente no encontrado")
    })
    public ResponseEntity<List<Horario>> obtenerHorarioDocente(
            @Parameter(description = "ID del docente") @PathVariable Long docenteId) {
        return docenteRepository.findById(docenteId)
                .map(docente -> ResponseEntity.ok(docente.getHorarios()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/docente/{docenteId}/dia/{dia}")
    @Operation(summary = "Obtener horario de un docente por día", description = "Devuelve las horas de un docente para un día concreto de la semana (1=Lunes, 5=Viernes)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horario del día obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Docente no encontrado")
    })
    public ResponseEntity<List<Horario>> obtenerHorarioPorDia(
            @Parameter(description = "ID del docente") @PathVariable Long docenteId,
            @Parameter(description = "Día de la semana (1=Lunes, 5=Viernes)") @PathVariable Integer dia) {
        return docenteRepository.findById(docenteId)
                .map(docente -> {
                    List<Horario> horariosDia = docente.getHorarios().stream()
                            .filter(h -> h.getDia() == dia)
                            .toList();
                    return ResponseEntity.ok(horariosDia);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

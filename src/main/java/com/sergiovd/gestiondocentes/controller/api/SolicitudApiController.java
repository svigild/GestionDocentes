package com.sergiovd.gestiondocentes.controller.api;

import com.sergiovd.gestiondocentes.model.AsuntoPropio;
import com.sergiovd.gestiondocentes.repository.AsuntoPropioRepository;
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
@RequestMapping("/api/solicitudes")
@Tag(name = "Solicitudes", description = "Operaciones relacionadas con las solicitudes de asuntos propios")
public class SolicitudApiController {

    @Autowired
    private AsuntoPropioRepository asuntoPropioRepository;

    @GetMapping
    @Operation(summary = "Listar todas las solicitudes", description = "Devuelve la lista completa de solicitudes de asuntos propios")
    @ApiResponse(responseCode = "200", description = "Lista de solicitudes obtenida correctamente")
    public List<AsuntoPropio> listarSolicitudes() {
        return asuntoPropioRepository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una solicitud por ID", description = "Devuelve los datos de una solicitud específica")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud encontrada"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    })
    public ResponseEntity<AsuntoPropio> obtenerSolicitud(
            @Parameter(description = "ID de la solicitud") @PathVariable Long id) {
        return asuntoPropioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/docente/{id}")
    @Operation(summary = "Listar solicitudes de un docente", description = "Devuelve las solicitudes de un docente específico")
    @ApiResponse(responseCode = "200", description = "Lista de solicitudes del docente obtenida correctamente")
    public List<AsuntoPropio> listarPorDocente(
            @Parameter(description = "ID del docente") @PathVariable Long id) {
        return asuntoPropioRepository.findByDocenteIdOrderByDiaSolicitadoDesc(id);
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Listar solicitudes pendientes", description = "Devuelve las solicitudes que aún no han sido aprobadas ni rechazadas")
    @ApiResponse(responseCode = "200", description = "Lista de solicitudes pendientes obtenida correctamente")
    public List<AsuntoPropio> listarPendientes() {
        return asuntoPropioRepository.findAll().stream()
                .filter(s -> s.getAprobado() == null)
                .toList();
    }

    @PutMapping("/{id}/aprobar")
    @Operation(summary = "Aprobar una solicitud", description = "Aprueba una solicitud de asunto propio pendiente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud aprobada correctamente"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    })
    public ResponseEntity<AsuntoPropio> aprobarSolicitud(
            @Parameter(description = "ID de la solicitud") @PathVariable Long id) {
        return asuntoPropioRepository.findById(id)
                .map(solicitud -> {
                    solicitud.setAprobado(true);
                    return ResponseEntity.ok(asuntoPropioRepository.save(solicitud));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar una solicitud", description = "Rechaza una solicitud de asunto propio pendiente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud rechazada correctamente"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    })
    public ResponseEntity<AsuntoPropio> rechazarSolicitud(
            @Parameter(description = "ID de la solicitud") @PathVariable Long id) {
        return asuntoPropioRepository.findById(id)
                .map(solicitud -> {
                    solicitud.setAprobado(false);
                    return ResponseEntity.ok(asuntoPropioRepository.save(solicitud));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

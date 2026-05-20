package com.sergiovd.gestiondocentes.controller.api;

import com.sergiovd.gestiondocentes.model.Departamento;
import com.sergiovd.gestiondocentes.repository.DepartamentoRepository;
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
@RequestMapping("/api/departamentos")
@Tag(name = "Departamentos", description = "Operaciones relacionadas con la gestión de departamentos")
public class DepartamentoApiController {

    @Autowired
    private DepartamentoRepository departamentoRepository;

    @GetMapping
    @Operation(summary = "Listar todos los departamentos", description = "Devuelve la lista completa de departamentos del centro")
    @ApiResponse(responseCode = "200", description = "Lista de departamentos obtenida correctamente")
    public List<Departamento> listarDepartamentos() {
        return departamentoRepository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un departamento por ID", description = "Devuelve los datos de un departamento específico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Departamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Departamento no encontrado")
    })
    public ResponseEntity<Departamento> obtenerDepartamento(
            @Parameter(description = "ID del departamento") @PathVariable Long id) {
        return departamentoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo departamento", description = "Registra un nuevo departamento en el sistema")
    @ApiResponse(responseCode = "200", description = "Departamento creado correctamente")
    public Departamento crearDepartamento(@RequestBody Departamento departamento) {
        return departamentoRepository.save(departamento);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un departamento", description = "Modifica los datos de un departamento existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Departamento actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "Departamento no encontrado")
    })
    public ResponseEntity<Departamento> actualizarDepartamento(
            @Parameter(description = "ID del departamento") @PathVariable Long id,
            @RequestBody Departamento departamento) {
        if (!departamentoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        departamento.setId(id);
        return ResponseEntity.ok(departamentoRepository.save(departamento));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un departamento", description = "Elimina un departamento del sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Departamento eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Departamento no encontrado")
    })
    public ResponseEntity<Void> eliminarDepartamento(
            @Parameter(description = "ID del departamento") @PathVariable Long id) {
        if (!departamentoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        departamentoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

package com.sergiovd.gestiondocentes.controller.api;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/docentes")
@Tag(name = "Docentes", description = "Operaciones relacionadas con la gestión de docentes")
public class DocenteApiController {

    @Autowired
    private DocenteRepository docenteRepository;

    @GetMapping
    @Operation(summary = "Listar todos los docentes", description = "Devuelve la lista completa de docentes registrados en el sistema")
    @ApiResponse(responseCode = "200", description = "Lista de docentes obtenida correctamente")
    public List<Docente> listarDocentes() {
        return docenteRepository.findAll();
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar docentes por nombre o apellidos", description = "Busca docentes cuyo nombre o apellidos contengan el texto indicado")
    @ApiResponse(responseCode = "200", description = "Resultados de búsqueda obtenidos correctamente")
    public List<Docente> buscarDocentes(@Parameter(description = "Texto de búsqueda") @RequestParam String q) {
        String busqueda = q.toLowerCase();
        return docenteRepository.findAll().stream()
                .filter(d -> (d.getNombre() != null && d.getNombre().toLowerCase().contains(busqueda))
                        || (d.getApellidos() != null && d.getApellidos().toLowerCase().contains(busqueda)))
                .toList();
    }

    @GetMapping("/departamento/{nombre}")
    @Operation(summary = "Listar docentes por departamento", description = "Devuelve los docentes pertenecientes a un departamento específico")
    @ApiResponse(responseCode = "200", description = "Lista de docentes del departamento obtenida correctamente")
    public List<Docente> listarPorDepartamento(
            @Parameter(description = "Nombre del departamento") @PathVariable String nombre) {
        return docenteRepository.findByDepartamentoNombre(nombre);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un docente por ID", description = "Devuelve los datos de un docente específico según su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Docente encontrado"),
            @ApiResponse(responseCode = "404", description = "Docente no encontrado")
    })
    public ResponseEntity<Docente> obtenerDocente(
            @Parameter(description = "ID del docente") @PathVariable Long id) {
        return docenteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo docente", description = "Registra un nuevo docente en el sistema")
    @ApiResponse(responseCode = "200", description = "Docente creado correctamente")
    public Docente crearDocente(@Valid @RequestBody Docente docente) {
        return docenteRepository.save(docente);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un docente", description = "Modifica los datos de un docente existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Docente actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "Docente no encontrado")
    })
    public ResponseEntity<Docente> actualizarDocente(
            @Parameter(description = "ID del docente") @PathVariable Long id,
            @Valid @RequestBody Docente docente) {
        if (!docenteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        docente.setId(id);
        return ResponseEntity.ok(docenteRepository.save(docente));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un docente", description = "Elimina un docente del sistema según su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Docente eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Docente no encontrado")
    })
    public ResponseEntity<Void> eliminarDocente(
            @Parameter(description = "ID del docente") @PathVariable Long id) {
        if (!docenteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        docenteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

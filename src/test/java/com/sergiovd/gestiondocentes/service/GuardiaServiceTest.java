package com.sergiovd.gestiondocentes.service;

import com.sergiovd.gestiondocentes.model.*;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.HorarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del algoritmo de asignación de guardias (3 pasos).
 * No requieren arrancar el contexto de Spring ni la base de datos: usan mocks puros.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuardiaService — Algoritmo de 3 pasos para asignación de guardias")
class GuardiaServiceTest {

    @Mock
    private DocenteRepository docenteRepo;

    @Mock
    private HorarioRepository horarioRepo;

    @InjectMocks
    private GuardiaService guardiaService;

    private Departamento deptoInformatica;
    private Departamento deptoIngles;
    private Docente ausente;
    private Docente companyDepto;
    private Docente companyCiclo;
    private Docente otroCualquiera;

    @BeforeEach
    void setUp() {
        deptoInformatica = new Departamento();
        deptoInformatica.setId(1L);
        deptoInformatica.setNombre("Informática");

        deptoIngles = new Departamento();
        deptoIngles.setId(2L);
        deptoIngles.setNombre("Inglés");

        ausente = new Docente();
        ausente.setId(100L);
        ausente.setDepartamento(deptoInformatica);
        ausente.setGuardiasRealizadas(0);

        companyDepto = new Docente();
        companyDepto.setId(101L);
        companyDepto.setDepartamento(deptoInformatica);
        companyDepto.setGuardiasRealizadas(2);

        companyCiclo = new Docente();
        companyCiclo.setId(102L);
        companyCiclo.setDepartamento(deptoIngles);
        companyCiclo.setGuardiasRealizadas(1);

        otroCualquiera = new Docente();
        otroCualquiera.setId(103L);
        otroCualquiera.setDepartamento(deptoIngles);
        otroCualquiera.setGuardiasRealizadas(5);
    }

    @Test
    @DisplayName("Paso 1: cuando hay un compañero del mismo departamento, se le asigna")
    void asignarGuardia_paso1_mismoDepartamento() {
        when(docenteRepo.findById(100L)).thenReturn(Optional.of(ausente));
        when(docenteRepo.findAll()).thenReturn(new ArrayList<>(Arrays.asList(
                ausente, companyDepto, companyCiclo, otroCualquiera)));

        Docente asignado = guardiaService.asignarGuardia(100L);

        assertThat(asignado).isNotNull();
        assertThat(asignado.getId()).isEqualTo(companyDepto.getId());
    }

    @Test
    @DisplayName("Paso 1: con dos compañeros del mismo depto, se asigna al que menos guardias lleva")
    void asignarGuardia_paso1_menosGuardias() {
        Docente menosGuardias = new Docente();
        menosGuardias.setId(104L);
        menosGuardias.setDepartamento(deptoInformatica);
        menosGuardias.setGuardiasRealizadas(0);

        when(docenteRepo.findById(100L)).thenReturn(Optional.of(ausente));
        when(docenteRepo.findAll()).thenReturn(new ArrayList<>(Arrays.asList(
                ausente, companyDepto, menosGuardias)));

        Docente asignado = guardiaService.asignarGuardia(100L);

        assertThat(asignado.getId()).isEqualTo(menosGuardias.getId());
        assertThat(asignado.getGuardiasRealizadas()).isLessThan(companyDepto.getGuardiasRealizadas());
    }

    @Test
    @DisplayName("Cuando no existe el docente ausente, devuelve null")
    void asignarGuardia_docenteInexistente_devuelveNull() {
        when(docenteRepo.findById(999L)).thenReturn(Optional.empty());

        Docente asignado = guardiaService.asignarGuardia(999L);

        assertThat(asignado).isNull();
    }

    @Test
    @DisplayName("Obtener criterio: el sustituto del mismo departamento devuelve descripción correcta")
    void obtenerCriterio_mismoDepartamento() {
        when(docenteRepo.findById(100L)).thenReturn(Optional.of(ausente));

        String criterio = guardiaService.obtenerCriterioAsignacion(100L, companyDepto);

        assertThat(criterio).contains("Mismo departamento");
        assertThat(criterio).contains("Informática");
    }

    @Test
    @DisplayName("Obtener criterio: si no hay sustituto, devuelve mensaje claro")
    void obtenerCriterio_sinSustituto() {
        String criterio = guardiaService.obtenerCriterioAsignacion(100L, null);

        assertThat(criterio).isEqualTo("No se encontró sustituto");
    }
}

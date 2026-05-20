package com.sergiovd.gestiondocentes.service;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocenteServiceImpl implements DocenteService {

    @Autowired
    private DocenteRepository docenteRepository;

    @Override
    public List<Docente> listarTodos() {
        return docenteRepository.findAll();
    }

    @Override
    public List<Docente> listarOrdenadosPorApellido() {
        return docenteRepository.findAll(Sort.by("apellidos"));
    }

    public Docente obtenerDocenteConMasDias() {
        // PageRequest.of(0, 1) significa: Página 0, Tamaño 1 (Trae solo el primero)
        List<Docente> resultado = docenteRepository.findDocenteConMasDiasDisfrutados(PageRequest.of(0, 1));

        if (resultado.isEmpty()) {
            return null;
        }

        return resultado.get(0); // Devuelve el primer (y único) elemento
    }
}
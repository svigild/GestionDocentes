package com.sergiovd.gestiondocentes.service;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private DocenteRepository docenteRepo;

    // Este método es el puente entre Spring Security y mi base de datos.
    // Cuando alguien intenta loguearse, Spring me llama aquí para que busque al usuario.
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Busco al docente por email, que actúa como nombre de usuario en mi sistema
        Docente d = docenteRepo.findDocenteByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        // Mapeo el rol almacenado en la base de datos al sistema de roles de Spring Security.
        // "Dirección" → ROLE_DIRECCION, "Jefatura" → ROLE_JEFATURA, "Profesor" → ROLE_PROFESOR.
        // De esta forma, puedo usar @PreAuthorize en los controladores en vez de comparar strings manualmente.
        String rolNombre = (d.getRol() != null && d.getRol().getNombre() != null)
                ? d.getRol().getNombre() : "Profesor";

        // Normalizo el nombre del rol para Spring Security: sin tildes y en mayúsculas
        String rolSpring = switch (rolNombre) {
            case "Dirección" -> "DIRECCION";
            case "Jefatura"  -> "JEFATURA";
            default          -> "PROFESOR";
        };

        return User.builder()
                .username(d.getEmail())
                .password(d.getPassword())
                .roles(rolSpring) // Spring añade automáticamente el prefijo ROLE_
                .build();
    }
}
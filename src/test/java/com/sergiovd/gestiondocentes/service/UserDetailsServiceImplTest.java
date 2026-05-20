package com.sergiovd.gestiondocentes.service;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.Rol;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests del mapeo de roles entre la base de datos y Spring Security.
 * Validan que cada nombre de rol en español se traduzca a la authority correcta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl — Mapeo de roles a Spring Security")
class UserDetailsServiceImplTest {

    @Mock
    private DocenteRepository docenteRepo;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private Docente crearDocente(String rolNombre) {
        Docente d = new Docente();
        d.setEmail("test@educastur.org");
        d.setPassword("hashedPassword");

        if (rolNombre != null) {
            Rol r = new Rol();
            r.setNombre(rolNombre);
            d.setRol(r);
        }
        return d;
    }

    @Test
    @DisplayName("El rol Dirección se mapea a ROLE_DIRECCION")
    void mapeaRolDireccion() {
        Docente d = crearDocente("Dirección");
        when(docenteRepo.findDocenteByEmail("test@educastur.org")).thenReturn(Optional.of(d));

        UserDetails details = userDetailsService.loadUserByUsername("test@educastur.org");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DIRECCION");
    }

    @Test
    @DisplayName("El rol Jefatura se mapea a ROLE_JEFATURA")
    void mapeaRolJefatura() {
        Docente d = crearDocente("Jefatura");
        when(docenteRepo.findDocenteByEmail("test@educastur.org")).thenReturn(Optional.of(d));

        UserDetails details = userDetailsService.loadUserByUsername("test@educastur.org");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_JEFATURA");
    }

    @Test
    @DisplayName("El rol Profesor se mapea a ROLE_PROFESOR")
    void mapeaRolProfesor() {
        Docente d = crearDocente("Profesor");
        when(docenteRepo.findDocenteByEmail("test@educastur.org")).thenReturn(Optional.of(d));

        UserDetails details = userDetailsService.loadUserByUsername("test@educastur.org");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_PROFESOR");
    }

    @Test
    @DisplayName("Sin rol asignado, el sistema cae al rol PROFESOR por seguridad")
    void sinRol_fallbackProfesor() {
        Docente d = crearDocente(null);
        when(docenteRepo.findDocenteByEmail("test@educastur.org")).thenReturn(Optional.of(d));

        UserDetails details = userDetailsService.loadUserByUsername("test@educastur.org");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_PROFESOR");
    }

    @Test
    @DisplayName("Email inexistente lanza UsernameNotFoundException")
    void emailNoExiste_lanzaExcepcion() {
        when(docenteRepo.findDocenteByEmail("inexistente@educastur.org"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("inexistente@educastur.org"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}

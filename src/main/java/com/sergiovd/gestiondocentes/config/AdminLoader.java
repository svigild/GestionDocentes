package com.sergiovd.gestiondocentes.config;

import com.sergiovd.gestiondocentes.model.Departamento;
import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.Rol;
import com.sergiovd.gestiondocentes.repository.DepartamentoRepository;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class AdminLoader {

    private static final Logger log = LoggerFactory.getLogger(AdminLoader.class);

    @Autowired PasswordEncoder encoder;

    @Bean
    @Order(1) // Se ejecuta ANTES que CsvDataLoader para que existan roles y departamentos
    CommandLineRunner initAdmin(DocenteRepository docenteRepo,
                                RolRepository rolRepo,
                                DepartamentoRepository deptRepo) {
        return args -> {

            // Compruebo si la base de datos de roles está vacía. Si es así, creo el rol de Dirección
            // para asegurar que la aplicación tenga al menos un nivel de permisos funcional al arrancar.
            if (rolRepo.count() == 0) {
                Rol rDir = new Rol(); rDir.setNombre("Dirección"); rDir.setOrden(1); rolRepo.save(rDir);
                Rol rJef = new Rol(); rJef.setNombre("Jefatura"); rJef.setOrden(2); rolRepo.save(rJef);
                Rol rProf = new Rol(); rProf.setNombre("Profesor"); rProf.setOrden(3); rolRepo.save(rProf);
            }

            if (deptRepo.count() == 0) {
                Departamento d1 = new Departamento(); d1.setNombre("Informática"); d1.setCodigo("IFC"); d1.setTelefono("985000001"); deptRepo.save(d1);
                Departamento d2 = new Departamento(); d2.setNombre("Inglés"); d2.setCodigo("ING"); d2.setTelefono("985000002"); deptRepo.save(d2);
                Departamento d3 = new Departamento(); d3.setNombre("FOL"); d3.setCodigo("FOL"); d3.setTelefono("985000003"); deptRepo.save(d3);
                Departamento d4 = new Departamento(); d4.setNombre("Fabricación Mecánica"); d4.setCodigo("FM"); d4.setTelefono("985000004"); deptRepo.save(d4);
                Departamento d5 = new Departamento(); d5.setNombre("Matemáticas"); d5.setCodigo("MAT"); d5.setTelefono("985000005"); deptRepo.save(d5);
            }

            // Busco si ya existe el usuario administrador en la base de datos utilizando su email.
            Docente admin = docenteRepo.findDocenteByEmail("admin@educastur.org").orElse(null);

            if (admin == null) {
                // Si no existe, instancio un nuevo objeto Docente con los datos del administrador principal.
                // Le asigno el rol y el departamento que acabo de asegurar que existen (IDs 1).
                admin = new Docente();
                admin.setNombre("Administrador");
                admin.setApellidos("Principal");
                admin.setEmail("admin@educastur.org");
                admin.setSiglas("ADM");
                admin.setRol(rolRepo.findAll().stream().filter(r -> r.getNombre().equals("Dirección")).findFirst().orElse(null));
                admin.setDepartamento(deptRepo.findAll().stream().filter(d -> d.getCodigo().equals("IFC")).findFirst().orElse(null));
                admin.setTipoFuncionario(1);
                admin.setNotaOposicion(10.0);
                admin.setFechaAntiguedad(LocalDate.of(2000, 1, 1));
                admin.setGuardiasRealizadas(0);

                log.info("AdminLoader: Creando usuario administrador...");
            } else {
                log.info("AdminLoader: Usuario administrador encontrado. Actualizando credenciales...");
            }

            // Esta parte es importante para el entorno de desarrollo: fuerzo la contraseña a '1234'
            // encriptándola de nuevo cada vez que arranca la aplicación.
            // Esto me permite recuperar el acceso fácilmente si olvido la clave o reinicio la base de datos.
            admin.setPassword(encoder.encode("1234"));
            admin.setPasswordChanged(true); // El admin no necesita cambiar la contraseña

            docenteRepo.save(admin);

            log.info("Contraseña de administrador restaurada correctamente");
        };
    }
}
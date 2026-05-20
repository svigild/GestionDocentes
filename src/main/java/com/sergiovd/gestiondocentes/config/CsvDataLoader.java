package com.sergiovd.gestiondocentes.config;

import com.sergiovd.gestiondocentes.model.*;
import com.sergiovd.gestiondocentes.repository.*;
import com.sergiovd.gestiondocentes.service.MailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@Configuration
@org.springframework.core.annotation.Order(2) // Se ejecuta DESPUÉS de AdminLoader para que ya existan roles y departamentos
public class CsvDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvDataLoader.class);

    @Autowired private DocenteRepository docenteRepo;
    @Autowired private DepartamentoRepository deptRepo;
    @Autowired private RolRepository rolRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CicloRepository cicloRepo;
    @Autowired private AsignaturaRepository asignaturaRepo;
    @Autowired private HorarioRepository horarioRepo;
    @Autowired private MailService mailService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Permite desactivar el envio masivo de emails al cargar el CSV inicial (datos de prueba)
    @org.springframework.beans.factory.annotation.Value("${app.csv.send-emails:false}")
    private boolean enviarEmailsCargaInicial;

    @Override
    public void run(String... args) throws Exception {

        // Compruebo si la tabla de docentes está vacía. Si es así, ejecuto la lógica de importación masiva
        // para inicializar el sistema con datos de prueba o reales desde un fichero CSV.
        if (docenteRepo.count() <= 1) {
            log.info("Cargando datos desde CSV...");
            try {
                ClassPathResource resource = new ClassPathResource("docentes.csv");
                if (resource.exists()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
                    String line;
                    boolean header = true;

                    // Itero línea a línea el archivo
                    while ((line = reader.readLine()) != null) {
                        // Me salto la cabecera para no intentar parsear los títulos de las columnas
                        if (header) { header = false; continue; }

                        // Separo los campos por comas, que es el formato estándar del CSV
                        String[] data = line.split(",");

                        // Valido que la línea tenga todos los campos necesarios para evitar errores de índice
                        // Formato CSV: Nombre, Apellidos, Email, Siglas, CodigoDept, Rol, TipoFuncionario, Nota
                        if(data.length < 8) continue;

                        // Saltar si es el admin (ya creado por AdminLoader)
                        if (data[2].trim().equals("admin@educastur.org")) continue;

                        Docente d = new Docente();
                        d.setNombre(data[0].trim());
                        d.setApellidos(data[1].trim());
                        d.setEmail(data[2].trim());
                        d.setSiglas(data[3].trim());

                        // Asigno una contraseña temporal encriptada y marco el usuario para que
                        // el sistema le obligue a cambiarla en su primer inicio de sesión.
                        d.setPassword(passwordEncoder.encode("1234"));
                        d.setPasswordChanged(false);

                        // Parseo los datos numéricos (columnas 6 y 7)
                        d.setTipoFuncionario(Integer.parseInt(data[6].trim()));
                        d.setNotaOposicion(Double.parseDouble(data[7].trim()));

                        // Inicializo valores por defecto para antigüedad y guardias
                        d.setFechaAntiguedad(LocalDate.now().minusYears(5));
                        d.setGuardiasRealizadas(0);

                        // Busco el departamento por código (columna 4) y el rol por nombre (columna 5)
                        String codigoDept = data[4].trim();
                        String nombreRol = data[5].trim();

                        Departamento dept = deptRepo.findAll().stream()
                                .filter(dp -> dp.getCodigo().equals(codigoDept))
                                .findFirst().orElse(deptRepo.findAll().stream().findFirst().orElse(null));
                        d.setDepartamento(dept);

                        Rol rol = rolRepo.findAll().stream()
                                .filter(r -> r.getNombre().equals(nombreRol))
                                .findFirst().orElse(rolRepo.findAll().stream().findFirst().orElse(null));
                        d.setRol(rol);

                        docenteRepo.save(d);

                        // Envío de email con credenciales al docente recién creado (requisito del enunciado:
                        // "se le enviará un mail al profesorado con su nombre de usuario y una contraseña temporal").
                        // Por defecto el envio en la carga inicial esta DESACTIVADO para no spamear los emails
                        // de prueba del CSV; se activa con la variable de entorno app.csv.send-emails=true.
                        if (enviarEmailsCargaInicial) {
                            String cuerpo = "Hola " + d.getNombre() + " " + d.getApellidos() + ",\n\n"
                                    + "Se ha creado una cuenta para usted en GestiónDocentes.\n\n"
                                    + "Usuario: " + d.getEmail() + "\n"
                                    + "Contraseña temporal: 1234\n\n"
                                    + "IMPORTANTE: La primera vez que acceda, el sistema le pedirá que cambie esta contraseña.\n\n"
                                    + "Acceda en: " + baseUrl + "/login\n\n"
                                    + "Un saludo,\nEquipo de GestiónDocentes";
                            mailService.send(d.getEmail(),
                                    d.getNombre() + " " + d.getApellidos(),
                                    "Credenciales de acceso — GestiónDocentes",
                                    cuerpo);
                        }
                    }
                    reader.close();
                    log.info("CSV cargado correctamente");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // --- CARGA DE HORARIOS DESDE CSV (si existe el fichero) ---
            // Si existe un fichero horarios.csv en resources, lo cargo.
            // Si no existe, genero horarios de ejemplo como fallback.
            boolean horariosCargados = cargarHorariosCsv();
            if (!horariosCargados) {
                // --- CARGA DE CICLOS, ASIGNATURAS Y HORARIOS DE EJEMPLO (FALLBACK) ---
                cargarHorariosEjemplo();
            }
        }
    }

    /**
     * Intenta cargar horarios desde un fichero CSV en resources.
     * Formato esperado: EmailDocente,Dia,Hora,Aula,SiglasAsignatura,CodigoCiclo,NombreAsignatura,Curso
     * Si el fichero no existe, devuelve false para que se generen horarios de ejemplo.
     */
    private boolean cargarHorariosCsv() {
        try {
            ClassPathResource resource = new ClassPathResource("horarios.csv");
            if (!resource.exists()) {
                log.info("No se encontró horarios.csv — se generarán horarios de ejemplo.");
                return false;
            }

            log.info("Cargando horarios desde CSV...");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            String line;
            boolean header = true;
            int cargados = 0;

            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }

                String[] data = line.split(",");
                // Formato mínimo: EmailDocente, Dia, Hora, Aula, SiglasAsignatura, CodigoCiclo, NombreAsignatura, Curso
                if (data.length < 5) continue;

                String emailDocente = data[0].trim();
                int dia = Integer.parseInt(data[1].trim());
                int hora = Integer.parseInt(data[2].trim());
                String aula = data[3].trim();
                String siglasAsignatura = data[4].trim();

                // Busco el docente por email
                Docente docente = docenteRepo.findDocenteByEmail(emailDocente).orElse(null);
                if (docente == null) continue;

                // Busco o creo la asignatura
                String codigoCiclo = data.length > 5 ? data[5].trim() : "";
                String nombreAsig = data.length > 6 ? data[6].trim() : siglasAsignatura;
                int curso = data.length > 7 ? Integer.parseInt(data[7].trim()) : 1;

                // Busco el ciclo si viene
                Ciclo ciclo = null;
                if (!codigoCiclo.isEmpty()) {
                    ciclo = cicloRepo.findAll().stream()
                            .filter(c -> c.getCodigo().equals(codigoCiclo))
                            .findFirst().orElse(null);
                    // Si no existe el ciclo, lo creo
                    if (ciclo == null) {
                        ciclo = new Ciclo();
                        ciclo.setCodigo(codigoCiclo);
                        ciclo.setNombre(codigoCiclo);
                        ciclo.setFamilia("General");
                        ciclo.setTurno("Mañana");
                        cicloRepo.save(ciclo);
                    }
                }

                // Busco la asignatura por siglas
                final Ciclo cicloFinal = ciclo;
                Asignatura asignatura = asignaturaRepo.findAll().stream()
                        .filter(a -> a.getSiglas().equals(siglasAsignatura))
                        .findFirst().orElse(null);

                if (asignatura == null) {
                    asignatura = new Asignatura();
                    asignatura.setSiglas(siglasAsignatura);
                    asignatura.setNombre(nombreAsig);
                    asignatura.setCurso(curso);
                    asignatura.setCiclo(cicloFinal);
                    asignaturaRepo.save(asignatura);
                }

                Horario h = new Horario();
                h.setDocente(docente);
                h.setDia(dia);
                h.setHora(hora);
                h.setAula(aula);
                h.setAsignatura(asignatura);
                horarioRepo.save(h);
                cargados++;
            }
            reader.close();
            log.info("Horarios CSV cargados: {} registros", cargados);
            return cargados > 0;

        } catch (Exception e) {
            log.error("Error cargando horarios.csv: {}", e.getMessage());
            return false;
        }
    }

    private void cargarHorariosEjemplo() {
        if (cicloRepo.count() > 0) return; // Ya hay datos

        log.info("Cargando ciclos, asignaturas y horarios de ejemplo...");

        // Crear ciclos formativos
        Ciclo dam = new Ciclo(); dam.setNombre("Desarrollo de Aplicaciones Multiplataforma"); dam.setFamilia("Informática"); dam.setCodigo("DAM"); dam.setTurno("Mañana");
        Ciclo daw = new Ciclo(); daw.setNombre("Desarrollo de Aplicaciones Web"); daw.setFamilia("Informática"); daw.setCodigo("DAW"); daw.setTurno("Mañana");
        Ciclo asir = new Ciclo(); asir.setNombre("Administración de Sistemas Informáticos en Red"); asir.setFamilia("Informática"); asir.setCodigo("ASIR"); asir.setTurno("Tarde");
        Ciclo mec = new Ciclo(); mec.setNombre("Fabricación Mecánica"); mec.setFamilia("Fabricación Mecánica"); mec.setCodigo("FM"); mec.setTurno("Mañana");

        cicloRepo.save(dam);
        cicloRepo.save(daw);
        cicloRepo.save(asir);
        cicloRepo.save(mec);

        // Crear asignaturas
        String[][] asigs = {
                {"Programación", "PRG", "1", "DAM"},
                {"Base de Datos", "BBD", "1", "DAM"},
                {"Entornos de Desarrollo", "EDD", "1", "DAM"},
                {"Sistemas Informáticos", "SIN", "1", "DAM"},
                {"Lenguajes de Marcas", "LMR", "1", "DAM"},
                {"Acceso a Datos", "ACD", "2", "DAM"},
                {"Desarrollo de Interfaces", "DIN", "2", "DAM"},
                {"Programación Multimedia", "PMM", "2", "DAM"},
                {"Diseño de Interfaces Web", "DIW", "2", "DAW"},
                {"Desarrollo Web en Entorno Cliente", "DWC", "2", "DAW"},
                {"Desarrollo Web en Entorno Servidor", "DWS", "2", "DAW"},
                {"Implantación de Sistemas", "ISO", "1", "ASIR"},
                {"Redes Locales", "RDL", "1", "ASIR"},
                {"Fundamentos de Hardware", "FHW", "1", "ASIR"},
                {"Fabricación por Arranque", "FAR", "1", "FM"},
                {"Metrología y Ensayos", "MYE", "1", "FM"},
                {"Interpretación Gráfica", "IGR", "1", "FM"},
        };

        for (String[] a : asigs) {
            Asignatura asig = new Asignatura();
            asig.setNombre(a[0]);
            asig.setSiglas(a[1]);
            asig.setCurso(Integer.parseInt(a[2]));
            // Buscar ciclo por código
            String codigoCiclo = a[3];
            Ciclo ciclo = cicloRepo.findAll().stream()
                    .filter(c -> c.getCodigo().equals(codigoCiclo))
                    .findFirst().orElse(null);
            asig.setCiclo(ciclo);
            asignaturaRepo.save(asig);
        }

        // Asignar horarios a cada docente
        List<Docente> docentes = docenteRepo.findAll();
        List<Asignatura> todasAsignaturas = asignaturaRepo.findAll();
        Random random = new Random(42); // Semilla fija para resultados consistentes
        String[] aulas = {"A01", "A02", "A03", "B01", "B02", "B03", "T01", "T02", "LAB1", "LAB2", "LAB3"};

        for (Docente docente : docentes) {
            // Cada docente tiene entre 15 y 22 horas semanales
            int horasSemana = 15 + random.nextInt(8);
            boolean[][] ocupado = new boolean[7][5]; // 7 horas x 5 días
            int horasAsignadas = 0;

            // Asignar 2-3 asignaturas por docente
            int numAsignaturas = 2 + random.nextInt(2);
            Asignatura[] misAsignaturas = new Asignatura[numAsignaturas];
            for (int i = 0; i < numAsignaturas; i++) {
                misAsignaturas[i] = todasAsignaturas.get(random.nextInt(todasAsignaturas.size()));
            }

            String aulaDocente = aulas[random.nextInt(aulas.length)];

            while (horasAsignadas < horasSemana) {
                int dia = random.nextInt(5) + 1;  // 1-5 (Lunes a Viernes)
                int hora = random.nextInt(7) + 1;  // 1-7

                if (!ocupado[hora - 1][dia - 1]) {
                    ocupado[hora - 1][dia - 1] = true;

                    Horario h = new Horario();
                    h.setDia(dia);
                    h.setHora(hora);
                    h.setAula(aulaDocente);
                    h.setDocente(docente);
                    h.setAsignatura(misAsignaturas[random.nextInt(misAsignaturas.length)]);
                    horarioRepo.save(h);

                    horasAsignadas++;
                }
            }
        }

        log.info("Horarios de ejemplo cargados: {} registros", horarioRepo.count());
    }
}
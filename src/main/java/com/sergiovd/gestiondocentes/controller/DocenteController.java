package com.sergiovd.gestiondocentes.controller;

import com.sergiovd.gestiondocentes.dto.SolicitudDTO;
import com.sergiovd.gestiondocentes.model.*;
import com.sergiovd.gestiondocentes.repository.*;
import com.sergiovd.gestiondocentes.service.DocenteService;
import com.sergiovd.gestiondocentes.service.GuardiaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DocenteController {

    private static final Logger log = LoggerFactory.getLogger(DocenteController.class);

    /** Extensiones de archivo permitidas para la subida de material justificativo */
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".jpg", ".jpeg", ".png", ".gif", ".txt", ".zip", ".rar"
    );

    @Autowired private DocenteService docenteService;
    @Autowired private DocenteRepository docenteRepo;
    @Autowired private AsuntoPropioRepository asuntoRepo;
    @Autowired private GuardiaService guardiaService;
    @Autowired(required = false) private JavaMailSender mailSender;
    @Autowired private DepartamentoRepository deptRepo;
    @Autowired private RolRepository rolRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HorarioRepository horarioRepo;
    @Autowired private AsignaturaRepository asignaturaRepo;
    @Autowired private FaltaRepository faltaRepo;

    // Leo el número máximo de permisos diarios desde el archivo de configuración para no tener números mágicos en el código
    @Value("${app.config.max-permisos-diarios:3}")
    private int MAX_PERMISOS_DIARIOS;

    // Direccion remitente y URL publica usadas en los emails que envia la aplicacion.
    @Value("${app.mail.from:no-reply@gestiondocentes.com}")
    private String mailFrom;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // --- PRIMER ACCESO: cambio de contraseña obligatorio ---
    // Muestro un formulario sencillo para que el docente recién creado cambie su contraseña temporal.
    // Como ya está autenticado por sesión, no necesito ni token ni email.
    @GetMapping("/web/primer-acceso")
    public String primerAccesoForm(Principal principal) {
        Docente d = docenteRepo.findDocenteByEmail(principal.getName()).orElse(null);
        // Si ya ha cambiado la contraseña, no debería estar aquí: lo mando al dashboard
        if (d == null || Boolean.TRUE.equals(d.getPasswordChanged())) {
            return "redirect:/";
        }
        return "auth/primer-acceso";
    }

    @PostMapping("/web/primer-acceso")
    public String primerAccesoGuardar(@RequestParam("password") String password,
                                      @RequestParam("password2") String password2,
                                      Principal principal,
                                      Model model) {
        // Validaciones mínimas: que coincidan y tengan al menos 4 caracteres
        if (password == null || password.length() < 4) {
            model.addAttribute("error", "La contraseña debe tener al menos 4 caracteres.");
            return "auth/primer-acceso";
        }
        if (!password.equals(password2)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return "auth/primer-acceso";
        }

        Docente d = docenteRepo.findDocenteByEmail(principal.getName()).orElse(null);
        if (d == null) return "redirect:/login";

        // Encripto y guardo, marcando el flag para que no vuelva a forzarse el cambio
        d.setPassword(passwordEncoder.encode(password));
        d.setPasswordChanged(true);
        docenteRepo.save(d);
        log.info("Cambio de contraseña inicial completado para {}", d.getEmail());

        return "redirect:/?passwordChanged=true";
    }

    // --- HOME & LISTADO ---
    @GetMapping("/")
    public String home(Model model, Principal principal) {
        // Implementé esta validación para detectar si el usuario acaba de entrar con la contraseña temporal '1234'
        // Si es así, le redirijo forzosamente a la pantalla de cambio de contraseña por seguridad
        Docente d = docenteRepo.findDocenteByEmail(principal.getName()).orElse(null);
        if (d != null && Boolean.FALSE.equals(d.getPasswordChanged())) {
            return "redirect:/web/primer-acceso";
        }

        long totalDocentes = docenteRepo.count();
        // Filtro las solicitudes que tienen 'aprobado' a null para saber cuántas hay pendientes de revisar
        long pendientes = asuntoRepo.findAll().stream().filter(a -> a.getAprobado() == null).count();

        // Obtengo todas las solicitudes para mostrarlas en el widget de actividad reciente
        List<AsuntoPropio> actividad = asuntoRepo.findAll();
        // Invierto la lista para mostrar primero las más nuevas (LIFO)
        Collections.reverse(actividad);
        // Me quedo solo con las 5 primeras para no saturar la interfaz del dashboard
        List<AsuntoPropio> ultimas5 = actividad.stream().limit(5).collect(Collectors.toList());

        model.addAttribute("totalDocentes", totalDocentes);
        model.addAttribute("solicitudesPendientes", pendientes);
        model.addAttribute("actividadReciente", ultimas5);

        return "index";
    }

    @GetMapping("/web/admin/estadisticas")
    @PreAuthorize("hasRole('DIRECCION')")
    public String verEstadisticas(Model model, Principal principal) {

        // Totales generales
        long totalDocentes = docenteRepo.count();
        List<AsuntoPropio> todasSolicitudes = asuntoRepo.findAll();
        long aprobadas = todasSolicitudes.stream().filter(a -> Boolean.TRUE.equals(a.getAprobado())).count();
        long rechazadas = todasSolicitudes.stream().filter(a -> Boolean.FALSE.equals(a.getAprobado())).count();
        long pendientesCount = todasSolicitudes.stream().filter(a -> a.getAprobado() == null).count();

        model.addAttribute("totalDocentes", totalDocentes);
        model.addAttribute("aprobadas", aprobadas);
        model.addAttribute("rechazadas", rechazadas);
        model.addAttribute("pendientesCount", pendientesCount);

        // Docentes por departamento
        Map<String, Long> docentesPorDepto = docenteRepo.findAll().stream()
                .filter(d -> d.getDepartamento() != null)
                .collect(Collectors.groupingBy(d -> d.getDepartamento().getNombre(), Collectors.counting()));
        model.addAttribute("docentesPorDepto", docentesPorDepto);

        // Top 5 de guardias realizadas
        List<Docente> topGuardias = docenteRepo.findAll().stream()
                .filter(d -> d.getGuardiasRealizadas() != null && d.getGuardiasRealizadas() > 0)
                .sorted(Comparator.comparing(Docente::getGuardiasRealizadas).reversed())
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("topGuardias", topGuardias);

        // Docente con más días disfrutados
        Docente masDisfruton = null;
        try { masDisfruton = asuntoRepo.encontrarDocenteConMasDias(); } catch (Exception ignored) {}
        model.addAttribute("masDisfruton", masDisfruton);

        // Distribución por tipo de funcionario
        List<Docente> todos = docenteRepo.findAll();
        long carrera = todos.stream().filter(d -> d.getTipoFuncionario() != null && d.getTipoFuncionario() == 1).count();
        long practicas = todos.stream().filter(d -> d.getTipoFuncionario() != null && d.getTipoFuncionario() == 2).count();
        long interinos = todos.stream().filter(d -> d.getTipoFuncionario() != null && d.getTipoFuncionario() == 3).count();
        model.addAttribute("carrera", carrera);
        model.addAttribute("practicas", practicas);
        model.addAttribute("interinos", interinos);

        return "admin/stats";
    }

    @GetMapping("/web/docentes")
    public String listarDocentes(@RequestParam(required = false) String departamento,
                                 @RequestParam(required = false) String busqueda,
                                 Model model) {
        List<Docente> docentes;

        // Implementé este if para el filtro por departamento. Si viene un parámetro en la URL, filtro la lista.
        // Si no, cargo la lista completa ordenada alfabéticamente para facilitar la búsqueda visual.
        if (departamento != null && !departamento.isEmpty() && !departamento.equals("Todos")) {
            docentes = docenteRepo.findByDepartamentoNombre(departamento);
        } else {
            docentes = docenteService.listarOrdenadosPorApellido();
        }

        // Filtro adicional por nombre o apellidos cuando el usuario usa la barra de búsqueda
        if (busqueda != null && !busqueda.trim().isEmpty()) {
            String termino = busqueda.trim().toLowerCase();
            docentes = docentes.stream()
                    .filter(d -> (d.getNombre() != null && d.getNombre().toLowerCase().contains(termino))
                            || (d.getApellidos() != null && d.getApellidos().toLowerCase().contains(termino))
                            || (d.getEmail() != null && d.getEmail().toLowerCase().contains(termino)))
                    .collect(Collectors.toList());
        }

        model.addAttribute("listaDocentes", docentes);
        model.addAttribute("departamentos", deptRepo.findAll());
        model.addAttribute("deptSeleccionado", departamento);
        model.addAttribute("busqueda", busqueda);

        return "docentes/list";
    }

    // --- SOLICITUDES ---
    @GetMapping("/web/solicitud/nueva")
    public String formSolicitud(Model model) {
        model.addAttribute("solicitud", new SolicitudDTO());
        // Paso la lista de docentes por si el admin quiere registrar una solicitud en nombre de otro
        model.addAttribute("docentes", docenteService.listarTodos());
        return "solicitudes/form";
    }

    @PostMapping("/web/solicitud/guardar")
    public String guardarSolicitud(@Valid @ModelAttribute("solicitudDto") SolicitudDTO solicitudDto,
                                   BindingResult bindingResult) {

        // Validación de constraints (Bean Validation): si falla, redirijo con error de formato
        if (bindingResult.hasErrors()) {
            log.warn("Validación de solicitud fallida: {}", bindingResult.getAllErrors());
            return "redirect:/web/solicitud/nueva?error=validacion";
        }

        // Validaciones de negocio:

        // 1. No permitir fechas pasadas
        if (solicitudDto.getDiaSolicitado().isBefore(LocalDate.now())) {
            return "redirect:/web/solicitud/nueva?error=fecha";
        }

        // 2. Control de cupo diario (Requisito del centro)
        // Cuento cuántas personas faltan ese día concreto
        long genteEseDia = asuntoRepo.findAll().stream()
                .filter(a -> a.getDiaSolicitado().equals(solicitudDto.getDiaSolicitado())
                        && Boolean.TRUE.equals(a.getAprobado()))
                .count();

        if (genteEseDia >= MAX_PERMISOS_DIARIOS) {
            return "redirect:/web/solicitud/nueva?error=cupo";
        }

        Docente d = docenteRepo.findById(solicitudDto.getIdDocente()).orElse(null);
        if (d == null) return "redirect:/web/solicitud/nueva?error=usuario";

        // 3. Control de trimestres (Solo 1 día por trimestre)
        // Calculo el trimestre matemático del mes solicitado
        int mesSolicitado = solicitudDto.getDiaSolicitado().getMonthValue();
        int trimestreSolicitado = (mesSolicitado - 1) / 3 + 1;

        // Compruebo si el docente ya ha gastado un día en ese mismo trimestre y año
        boolean yaGastado = d.getAsuntosPropios().stream()
                .filter(a -> Boolean.TRUE.equals(a.getAprobado()))
                .anyMatch(a -> {
                    int m = a.getDiaSolicitado().getMonthValue();
                    int t = (m - 1) / 3 + 1;
                    return t == trimestreSolicitado &&
                            a.getDiaSolicitado().getYear() == solicitudDto.getDiaSolicitado().getYear();
                });

        if (yaGastado) {
            return "redirect:/web/solicitud/nueva?error=trimestre_agotado";
        }

        AsuntoPropio asunto = new AsuntoPropio();
        asunto.setDiaSolicitado(solicitudDto.getDiaSolicitado());
        asunto.setDescripcion(solicitudDto.getDescripcion());
        asunto.setDocente(d);
        asunto.setFechaTramitacion(LocalDateTime.now());
        asunto.setAprobado(null); // Inicialmente pendiente

        asuntoRepo.save(asunto);

        return "redirect:/web/solicitudes/mis-solicitudes/" + d.getId();
    }

    @PostMapping("/web/solicitud/subir-material")
    public String subirMaterial(@RequestParam("idSolicitud") Long idSolicitud,
                                @RequestParam("archivo") MultipartFile archivo) {
        AsuntoPropio ap = asuntoRepo.findById(idSolicitud).orElse(null);
        if (ap == null) return "redirect:/";

        try {
            if (!archivo.isEmpty()) {
                // Validación de seguridad: solo se permiten extensiones de archivo seguras
                String nombreOriginal = archivo.getOriginalFilename();
                String extension = nombreOriginal != null && nombreOriginal.contains(".")
                        ? nombreOriginal.substring(nombreOriginal.lastIndexOf(".")).toLowerCase()
                        : "";

                if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
                    log.warn("Intento de subida de archivo con extensión no permitida: {}", extension);
                    return "redirect:/web/solicitudes/mis-solicitudes/" + ap.getDocente().getId() + "?errorFormato";
                }

                // Nombre seguro: ID de solicitud + extensión (evita path traversal)
                String nombreSeguro = "solicitud_" + idSolicitud + "_" + System.currentTimeMillis() + extension;
                String carpeta = "uploads/";
                Path path = Paths.get(carpeta + nombreSeguro);
                Files.createDirectories(path.getParent());
                Files.write(path, archivo.getBytes());
                log.info("Material subido correctamente: {} -> {}", nombreOriginal, nombreSeguro);
            }
        } catch (IOException e) {
            log.error("Error al subir material para solicitud {}: {}", idSolicitud, e.getMessage());
        }
        return "redirect:/web/solicitudes/mis-solicitudes/" + ap.getDocente().getId() + "?uploadSuccess";
    }

    @GetMapping("/web/solicitudes/mis-solicitudes/{idDocente}")
    public String misSolicitudes(@PathVariable Long idDocente, Model model) {
        Docente d = docenteRepo.findById(idDocente).orElse(null);

        if (d != null) {
            model.addAttribute("docente", d);

            // Decidí usar el repositorio directamente en vez de d.getAsuntosPropios() para evitar problemas de caché de Hibernate.
            // Así me aseguro de que siempre veo la lista actualizada en tiempo real.
            List<AsuntoPropio> listaReal = asuntoRepo.findByDocenteIdOrderByDiaSolicitadoDesc(idDocente);
            model.addAttribute("misAsuntos", listaReal);

            // Calculo estadísticas al vuelo para mostrar los contadores de colores en la vista
            long diasPendientes = listaReal.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getAprobado()) && !a.getDiaSolicitado().isBefore(LocalDate.now()))
                    .count();

            long diasGastados = listaReal.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getAprobado()) && a.getDiaSolicitado().isBefore(LocalDate.now()))
                    .count();

            model.addAttribute("statsPendientes", diasPendientes);
            model.addAttribute("statsGastados", diasGastados);
        }
        return "solicitudes/my-list";
    }

    // --- ADMIN ---
    @GetMapping("/web/admin/validar")
    @PreAuthorize("hasRole('DIRECCION')")
    public String panelValidacion(Model model, Principal principal) {

        List<AsuntoPropio> todas = asuntoRepo.findAll();
        List<AsuntoPropio> pendientes = todas.stream().filter(a -> a.getAprobado() == null).collect(Collectors.toList());

        // Implementé un comparador complejo para ordenar las solicitudes pendientes según baremo
        // (tipo de funcionario -> antigüedad -> nota oposición), útil para desempatar si faltan plazas
        pendientes.sort(
                Comparator.comparing((AsuntoPropio a) -> a.getDocente().getTipoFuncionario(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.getDocente().getFechaAntiguedad(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.getDocente().getNotaOposicion(), Comparator.nullsLast(Comparator.reverseOrder()))
        );

        List<AsuntoPropio> resueltas = todas.stream().filter(a -> a.getAprobado() != null).toList();
        pendientes.addAll(resueltas);
        model.addAttribute("todasSolicitudes", pendientes);
        return "solicitudes/admin-list";
    }

    @PostMapping("/web/admin/accion/{id}/{estado}")
    @PreAuthorize("hasRole('DIRECCION')")
    public String validarSolicitud(@PathVariable Long id, @PathVariable Boolean estado) {
        AsuntoPropio ap = asuntoRepo.findById(id).orElse(null);
        if (ap != null) {
            ap.setAprobado(estado);
            asuntoRepo.save(ap);

            // Notificación por email al docente con el resultado de la solicitud
            if (mailSender != null) {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(mailFrom);
                    msg.setTo(ap.getDocente().getEmail());
                    msg.setSubject("Resolución de su solicitud de asuntos propios — GestiónDocentes");
                    String cuerpo = "Hola " + ap.getDocente().getNombre() + ",\n\n"
                            + "Su solicitud de día de asuntos propios para el "
                            + ap.getDiaSolicitado()
                            + " ha sido " + (estado ? "APROBADA" : "DENEGADA") + ".\n\n"
                            + "Puede consultar el detalle accediendo a: " + baseUrl + "/\n\n"
                            + "Un saludo,\nEquipo de GestiónDocentes";
                    msg.setText(cuerpo);
                    mailSender.send(msg);
                    log.info("Notificación de {} enviada a {}", estado ? "aprobación" : "rechazo", ap.getDocente().getEmail());
                } catch (Exception e) {
                    log.warn("Fallo al enviar email de notificación a {}: {}", ap.getDocente().getEmail(), e.getMessage());
                }
            }
        }
        return "redirect:/web/admin/validar";
    }

    // --- GUARDIAS (Visualizacion del Cuadrante Mensual) ---
    @GetMapping("/web/guardias/panel")
    public String panelGuardias(Model model) {
        // Calculo los datos del mes actual para poder dibujar el calendario dinámicamente
        LocalDate hoy = LocalDate.now();
        int diasEnElMes = hoy.lengthOfMonth();

        // Calculo el día de la semana que empieza el mes para pintar los huecos vacíos antes del día 1
        int diaSemanaInicio = hoy.withDayOfMonth(1).getDayOfWeek().getValue() - 1;

        // Recupero todas las ausencias aprobadas de este mes para mostrarlas en el calendario
        List<AsuntoPropio> ausenciasMes = asuntoRepo.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getAprobado()))
                .filter(a -> a.getDiaSolicitado().getMonth() == hoy.getMonth() && a.getDiaSolicitado().getYear() == hoy.getYear())
                .toList();

        // Agrupo las ausencias por día (Key: Día del mes, Value: Lista de ausencias)
        // Esto facilita mucho pintar la vista en Thymeleaf iterando por días
        Map<Integer, List<AsuntoPropio>> mapaAusencias = ausenciasMes.stream()
                .collect(Collectors.groupingBy(a -> a.getDiaSolicitado().getDayOfMonth()));

        // Filtro la lista de docentes para el desplegable: solo muestro los que realmente faltan este mes
        // para facilitar la tarea al Jefe de Estudios
        Set<Long> idsAusentes = ausenciasMes.stream()
                .map(a -> a.getDocente().getId())
                .collect(Collectors.toSet());

        List<Docente> docentesConFaltas = docenteService.listarTodos().stream()
                .filter(d -> idsAusentes.contains(d.getId()))
                .toList();

        // Si no hay faltas, cargo todos los docentes como fallback para que la interfaz no se rompa
        if (docentesConFaltas.isEmpty()) docentesConFaltas = docenteService.listarTodos();

        model.addAttribute("mesActual", hoy.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "ES")).toUpperCase());
        model.addAttribute("anioActual", hoy.getYear());
        model.addAttribute("diasEnElMes", diasEnElMes);
        model.addAttribute("diaSemanaInicio", diaSemanaInicio);
        model.addAttribute("mapaAusencias", mapaAusencias);
        model.addAttribute("docentes", docentesConFaltas);
        model.addAttribute("hoy", hoy.getDayOfMonth());

        return "guardias/panel";
    }

    // =====================================================================
    // --- EXPORTAR CSV (Descarga de fichero con datos del claustro) ---
    // IMPORTANTE: va ANTES de /web/docentes/{id} para que Spring no confunda "exportar" con un ID
    // =====================================================================
    @GetMapping("/web/docentes/exportar")
    public void exportarCSV(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=claustro_docentes.csv");

        // Uso OutputStream para poder escribir el BOM y luego el texto CSV
        java.io.OutputStream out = response.getOutputStream();
        // BOM para que Excel reconozca UTF-8 correctamente
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        StringBuilder sb = new StringBuilder();
        sb.append("Nombre,Apellidos,Email,Siglas,Departamento,Rol,Tipo Funcionario,Nota Oposicion,Antiguedad,Guardias Realizadas\n");

        List<Docente> docentes = docenteService.listarOrdenadosPorApellido();
        for (Docente d : docentes) {
            String tipo = "";
            if (d.getTipoFuncionario() != null) {
                switch (d.getTipoFuncionario()) {
                    case 1: tipo = "Carrera"; break;
                    case 2: tipo = "Practicas"; break;
                    case 3: tipo = "Interino"; break;
                }
            }
            sb.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    d.getNombre() != null ? d.getNombre() : "",
                    d.getApellidos() != null ? d.getApellidos() : "",
                    d.getEmail() != null ? d.getEmail() : "",
                    d.getSiglas() != null ? d.getSiglas() : "",
                    d.getDepartamento() != null ? d.getDepartamento().getNombre() : "",
                    d.getRol() != null ? d.getRol().getNombre() : "",
                    tipo,
                    d.getNotaOposicion() != null ? d.getNotaOposicion() : "",
                    d.getFechaAntiguedad() != null ? d.getFechaAntiguedad() : "",
                    d.getGuardiasRealizadas() != null ? d.getGuardiasRealizadas() : 0));
        }
        out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    // --- EDITAR DOCENTE (va ANTES de {id}) ---
    @GetMapping("/web/docentes/editar/{id}")
    public String formEditarDocente(@PathVariable Long id, Model model) {
        Docente docente = docenteRepo.findById(id).orElse(null);
        if (docente == null) return "redirect:/web/docentes";
        model.addAttribute("docente", docente);
        model.addAttribute("departamentos", deptRepo.findAll());
        model.addAttribute("roles", rolRepo.findAll());
        return "docentes/form";
    }

    @PostMapping("/web/docentes/guardar-edicion")
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String guardarEdicion(@Valid @ModelAttribute("docente") Docente docente,
                                 BindingResult bindingResult,
                                 Model model) {
        // Si los datos no cumplen las restricciones de validación, vuelvo al formulario con los errores
        if (bindingResult.hasErrors()) {
            log.warn("Validación de edición de docente fallida: {}", bindingResult.getAllErrors());
            model.addAttribute("departamentos", deptRepo.findAll());
            model.addAttribute("roles", rolRepo.findAll());
            return "docentes/form";
        }
        Docente existente = docenteRepo.findById(docente.getId()).orElse(null);
        if (existente == null) return "redirect:/web/docentes";
        existente.setNombre(docente.getNombre());
        existente.setApellidos(docente.getApellidos());
        existente.setEmail(docente.getEmail());
        existente.setSiglas(docente.getSiglas());
        existente.setDepartamento(docente.getDepartamento());
        existente.setRol(docente.getRol());
        existente.setTipoFuncionario(docente.getTipoFuncionario());
        existente.setNotaOposicion(docente.getNotaOposicion());
        existente.setFechaAntiguedad(docente.getFechaAntiguedad());
        docenteRepo.save(existente);
        return "redirect:/web/docentes?editado=true";
    }

    // --- ELIMINAR DOCENTE (solo Dirección) ---
    @PostMapping("/web/docentes/eliminar/{id}")
    @Transactional
    @PreAuthorize("hasRole('DIRECCION')")
    public String eliminarDocente(@PathVariable Long id, Principal principal) {
        Docente docente = docenteRepo.findById(id).orElse(null);
        if (docente != null) {
            horarioRepo.deleteByDocenteId(id);
            List<AsuntoPropio> solicitudes = asuntoRepo.findByDocenteIdOrderByDiaSolicitadoDesc(id);
            asuntoRepo.deleteAll(solicitudes);
            docenteRepo.delete(docente);
        }
        return "redirect:/web/docentes?eliminado=true";
    }

    // --- PERFIL DETALLADO ---
    @GetMapping("/web/docentes/{id}")
    public String perfilDocente(@PathVariable Long id, Model model) {
        Docente docente = docenteRepo.findById(id).orElse(null);
        if (docente == null) return "redirect:/web/docentes";

        // Horario semanal organizado en una matriz [hora][dia]
        // Creo una estructura de 7 horas x 5 días para pintar la tabla del horario
        String[][] matrizHorario = new String[7][5];
        String[][] matrizAula = new String[7][5];
        if (docente.getHorarios() != null) {
            for (Horario h : docente.getHorarios()) {
                int fila = h.getHora() - 1;
                int col = h.getDia() - 1;
                if (fila >= 0 && fila < 7 && col >= 0 && col < 5) {
                    matrizHorario[fila][col] = h.getAsignatura() != null ? h.getAsignatura().getSiglas() : "—";
                    matrizAula[fila][col] = h.getAula() != null ? h.getAula() : "";
                }
            }
        }

        // Solicitudes del docente
        List<AsuntoPropio> solicitudes = asuntoRepo.findByDocenteIdOrderByDiaSolicitadoDesc(id);
        long aprobadas = solicitudes.stream().filter(s -> Boolean.TRUE.equals(s.getAprobado())).count();
        long rechazadas = solicitudes.stream().filter(s -> Boolean.FALSE.equals(s.getAprobado())).count();
        long pendientes = solicitudes.stream().filter(s -> s.getAprobado() == null).count();

        model.addAttribute("docente", docente);
        model.addAttribute("matrizHorario", matrizHorario);
        model.addAttribute("matrizAula", matrizAula);
        model.addAttribute("solicitudes", solicitudes);
        model.addAttribute("aprobadas", aprobadas);
        model.addAttribute("rechazadas", rechazadas);
        model.addAttribute("pendientes", pendientes);

        return "docentes/perfil";
    }

    @GetMapping("/web/docentes/crear")
    public String formNuevoDocente(Model model) {
        model.addAttribute("docente", new Docente());
        model.addAttribute("departamentos", deptRepo.findAll());
        model.addAttribute("roles", rolRepo.findAll());
        return "docentes/form";
    }

    @PostMapping("/web/docentes/guardar-nuevo")
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String guardarNuevoDocente(@Valid @ModelAttribute("docente") Docente docente,
                                      BindingResult bindingResult,
                                      Model model) {
        // Validación previa de constraints. Si falla, vuelvo al formulario con los mensajes de error
        if (bindingResult.hasErrors()) {
            log.warn("Validación de nuevo docente fallida: {}", bindingResult.getAllErrors());
            model.addAttribute("departamentos", deptRepo.findAll());
            model.addAttribute("roles", rolRepo.findAll());
            return "docentes/form";
        }
        // Genero una contraseña temporal y fuerzo al usuario a cambiarla en el primer login
        String passTemporal = "1234";
        docente.setPassword(passwordEncoder.encode(passTemporal));
        docente.setPasswordChanged(false);

        // Inicializo valores por defecto para evitar NullPointerExceptions más adelante
        if (docente.getGuardiasRealizadas() == null) docente.setGuardiasRealizadas(0);
        if (docente.getFechaAntiguedad() == null) docente.setFechaAntiguedad(LocalDate.now());

        docenteRepo.save(docente);

        // Envío las credenciales por correo
        if (mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(docente.getEmail());
                msg.setSubject("Credenciales de acceso — GestiónDocentes");
                msg.setText("Hola " + docente.getNombre() + " " + docente.getApellidos() + ",\n\n"
                        + "Se ha creado una cuenta para usted en GestiónDocentes.\n\n"
                        + "Usuario: " + docente.getEmail() + "\n"
                        + "Contraseña temporal: " + passTemporal + "\n\n"
                        + "IMPORTANTE: La primera vez que acceda, el sistema le pedirá que cambie esta contraseña.\n\n"
                        + "Acceda en: " + baseUrl + "/login\n\n"
                        + "Un saludo,\nEquipo de GestiónDocentes");
                mailSender.send(msg);
            } catch (Exception e) {
                log.warn("Error enviando email de credenciales: {}", e.getMessage());
            }
        }

        return "redirect:/web/docentes?creado=true";
    }

    // --- ALGORITMO DE ASIGNACION (3 pasos según enunciado) ---
    @PostMapping("/web/guardias/asignar")
    public String asignarGuardia(@RequestParam Long idDocenteAusente, Model model) {
        Docente ausente = docenteRepo.findById(idDocenteAusente).orElse(null);
        if (ausente == null) return "redirect:/web/guardias/panel";

        // Delego en el servicio que implementa los 3 pasos del algoritmo:
        // Paso 1: Mismo departamento + menos guardias
        // Paso 2: Mismo grupo/ciclo + menos guardias
        // Paso 3: Cualquier docente + menos guardias
        Docente sustituto = guardiaService.asignarGuardia(ausente.getId());

        // Obtengo el criterio por el que se eligió para mostrarlo en la vista
        String criterio = guardiaService.obtenerCriterioAsignacion(ausente.getId(), sustituto);

        if (sustituto != null) {
            // Actualizo la carga de trabajo del sustituto incrementando su contador
            int actuales = sustituto.getGuardiasRealizadas() == null ? 0 : sustituto.getGuardiasRealizadas();
            sustituto.setGuardiasRealizadas(actuales + 1);
            docenteRepo.save(sustituto);

            // Busco todas las ausencias pendientes de cubrir de este profesor (aprobadas y futuras)
            List<AsuntoPropio> ausenciasSinCubrir = asuntoRepo.findAll().stream()
                    .filter(a -> a.getDocente().getId().equals(ausente.getId()))
                    .filter(a -> Boolean.TRUE.equals(a.getAprobado()))
                    .filter(a -> a.getSustituto() == null)
                    .filter(a -> !a.getDiaSolicitado().isBefore(LocalDate.now()))
                    .toList();

            for (AsuntoPropio ausencia : ausenciasSinCubrir) {
                ausencia.setSustituto(sustituto);
                asuntoRepo.save(ausencia);
            }

            // También asigno el sustituto a las faltas registradas sin cubrir de hoy en adelante
            List<Falta> faltasSinCubrir = faltaRepo.findByDocenteId(ausente.getId()).stream()
                    .filter(f -> f.getSustituto() == null)
                    .filter(f -> f.getFecha() != null && !f.getFecha().isBefore(LocalDate.now()))
                    .toList();
            for (Falta falta : faltasSinCubrir) {
                falta.setSustituto(sustituto);
                faltaRepo.save(falta);
            }
        }

        model.addAttribute("ausente", ausente);
        model.addAttribute("sustituto", sustituto);
        model.addAttribute("criterio", criterio);
        return "guardias/resultado";
    }

    // =====================================================================
    // --- CUADRANTE DIARIO DE GUARDIAS (Vista hora a hora de un día) ---
    // =====================================================================
    @GetMapping("/web/guardias/diario")
    public String cuadranteDiario(@RequestParam(required = false) String fecha, Model model) {
        LocalDate dia = (fecha != null && !fecha.isEmpty()) ? LocalDate.parse(fecha) : LocalDate.now();

        // Calculo el día de la semana (1=Lunes, 5=Viernes) para buscar en los horarios
        int diaSemana = dia.getDayOfWeek().getValue();
        if (diaSemana > 5) {
            // Fin de semana: redirijo al viernes anterior
            dia = dia.minusDays(diaSemana - 5);
            diaSemana = 5;
        }

        // Para cada hora (1-7), busco quién falta y quién cubre
        List<Map<String, Object>> filas = new ArrayList<>();

        for (int hora = 1; hora <= 7; hora++) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("hora", hora);

            // Busco faltas registradas en este día y hora
            final int horaFinal = hora;
            final int diaFinal = diaSemana;
            List<Falta> faltasEstaHora = faltaRepo.findByFecha(dia).stream()
                    .filter(f -> f.getHorario() != null
                            && f.getHorario().getDia() != null && f.getHorario().getDia().equals(diaFinal)
                            && f.getHorario().getHora() != null && f.getHorario().getHora().equals(horaFinal))
                    .collect(Collectors.toList());

            fila.put("faltas", faltasEstaHora);
            filas.add(fila);
        }

        String[] diasSemana = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
        model.addAttribute("filas", filas);
        model.addAttribute("fechaSeleccionada", dia);
        model.addAttribute("nombreDia", diasSemana[diaSemana]);
        model.addAttribute("diaSemana", diaSemana);

        return "guardias/diario";
    }

    // =====================================================================
    // --- MARCAR GUARDIA COMO REALIZADA / NO REALIZADA ---
    // =====================================================================
    @PostMapping("/web/faltas/marcar/{id}/{estado}")
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String marcarGuardiaRealizada(@PathVariable Long id, @PathVariable Boolean estado) {
        Falta falta = faltaRepo.findById(id).orElse(null);
        if (falta != null) {
            falta.setRealizada(estado);
            faltaRepo.save(falta);
        }
        return "redirect:/web/faltas";
    }

    // =====================================================================
    // --- MI HORARIO (Vista semanal del docente autenticado) ---
    // =====================================================================
    @GetMapping("/web/horarios")
    public String miHorario(Model model, Principal principal) {
        Docente docente = docenteRepo.findDocenteByEmail(principal.getName()).orElse(null);
        if (docente == null) return "redirect:/";

        // Construyo la matriz 7 horas x 5 días para la rejilla semanal
        String[][] matrizHorario = new String[7][5];
        String[][] matrizAula = new String[7][5];

        List<Horario> horarios = horarioRepo.findByDocenteId(docente.getId());
        for (Horario h : horarios) {
            int fila = h.getHora() - 1;
            int col = h.getDia() - 1;
            if (fila >= 0 && fila < 7 && col >= 0 && col < 5) {
                matrizHorario[fila][col] = h.getAsignatura() != null ? h.getAsignatura().getSiglas() : "—";
                matrizAula[fila][col] = h.getAula() != null ? h.getAula() : "";
            }
        }

        model.addAttribute("docente", docente);
        model.addAttribute("matrizHorario", matrizHorario);
        model.addAttribute("matrizAula", matrizAula);
        return "horarios/ver";
    }

    // =====================================================================
    // --- GESTIONAR HORARIOS (Asignar franjas - solo Dirección/Jefatura) ---
    // =====================================================================
    @GetMapping("/web/horarios/gestionar")
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String gestionarHorarios(Model model, Principal principal) {

        model.addAttribute("docentes", docenteService.listarOrdenadosPorApellido());
        model.addAttribute("asignaturas", asignaturaRepo.findAll());

        // Muestro las últimas 50 franjas horarias asignadas para referencia
        List<Horario> horarios = horarioRepo.findAll();
        // Ordeno por docente apellido + día + hora
        horarios.sort(Comparator.comparing((Horario h) -> h.getDocente() != null ? h.getDocente().getApellidos() : "")
                .thenComparing(Horario::getDia, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Horario::getHora, Comparator.nullsLast(Comparator.naturalOrder())));
        if (horarios.size() > 50) horarios = horarios.subList(0, 50);

        model.addAttribute("horarios", horarios);
        return "horarios/gestionar";
    }

    @PostMapping("/web/horarios/gestionar/guardar")
    public String guardarFranjaHoraria(@RequestParam Long docenteId,
                                       @RequestParam Integer dia,
                                       @RequestParam Integer hora,
                                       @RequestParam Long asignaturaId,
                                       @RequestParam String aula) {
        Horario h = new Horario();
        h.setDocente(docenteRepo.findById(docenteId).orElse(null));
        h.setDia(dia);
        h.setHora(hora);
        h.setAsignatura(asignaturaRepo.findById(asignaturaId).orElse(null));
        h.setAula(aula);
        horarioRepo.save(h);
        return "redirect:/web/horarios/gestionar?guardado=true";
    }

    @PostMapping("/web/horarios/gestionar/eliminar/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String eliminarFranjaHoraria(@PathVariable Long id) {
        horarioRepo.deleteById(id);
        return "redirect:/web/horarios/gestionar?eliminado=true";
    }

    // =====================================================================
    // --- UC4: INTRODUCIR FALTAS (Registrar ausencias en franjas horarias) ---
    // =====================================================================
    @GetMapping("/web/faltas")
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String panelFaltas(Model model, Principal principal) {

        model.addAttribute("docentes", docenteService.listarOrdenadosPorApellido());

        // Cargo las últimas 50 faltas registradas, ordenadas por fecha descendente
        List<Falta> faltas = faltaRepo.findAll();
        faltas.sort(Comparator.comparing(Falta::getFecha, Comparator.nullsLast(Comparator.reverseOrder())));
        if (faltas.size() > 50) faltas = faltas.subList(0, 50);
        model.addAttribute("faltas", faltas);

        return "faltas/introducir";
    }

    @PostMapping("/web/faltas/guardar")
    public String guardarFalta(@RequestParam Long docenteId,
                               @RequestParam String fecha,
                               @RequestParam Integer dia,
                               @RequestParam Integer hora,
                               @RequestParam(required = false) String anotacion,
                               @RequestParam(required = false) String material) {

        LocalDate fechaFalta = LocalDate.parse(fecha);

        // Busco la franja horaria del docente en ese día y hora
        List<Horario> horariosDocente = horarioRepo.findByDocenteId(docenteId);
        Horario horarioAfectado = horariosDocente.stream()
                .filter(h -> h.getDia() != null && h.getDia().equals(dia) && h.getHora() != null && h.getHora().equals(hora))
                .findFirst()
                .orElse(null);

        if (horarioAfectado == null) {
            // El docente no tiene clase en esa franja, no se puede registrar falta
            return "redirect:/web/faltas?error=true";
        }

        Falta falta = new Falta();
        falta.setFecha(fechaFalta);
        falta.setHorario(horarioAfectado);
        falta.setAnotacion(anotacion);
        falta.setMaterial(material);
        faltaRepo.save(falta);

        return "redirect:/web/faltas?guardado=true";
    }

    @PostMapping("/web/faltas/eliminar/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('DIRECCION', 'JEFATURA')")
    public String eliminarFalta(@PathVariable Long id) {
        faltaRepo.deleteById(id);
        return "redirect:/web/faltas?eliminado=true";
    }
}
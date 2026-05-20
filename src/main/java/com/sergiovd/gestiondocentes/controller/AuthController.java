package com.sergiovd.gestiondocentes.controller;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.PasswordResetToken;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.TokenRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired private DocenteRepository docenteRepo;
    @Autowired private TokenRepository tokenRepo;
    @Autowired(required = false) private JavaMailSender mailSender;
    @Autowired private PasswordEncoder passwordEncoder;

    // URL publica de la aplicacion: en local 'http://localhost:8080', en produccion la de Render.
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Direccion remitente que aparecera en el campo 'From' de los correos.
    @Value("${app.mail.from:no-reply@gestiondocentes.com}")
    private String mailFrom;

    // Mapeo para mostrar la vista personalizada de inicio de sesión
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    // Mapeo para mostrar el formulario de solicitud de recuperación de contraseña
    @GetMapping("/auth/forgot-password")
    public String forgotPasswordForm() {
        return "auth/forgot-password";
    }

    // Proceso la solicitud de recuperación. Busco al usuario y, si existe, genero un token seguro.
    @PostMapping("/auth/forgot-password")
    @Transactional
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        Docente docente = docenteRepo.findDocenteByEmail(email).orElse(null);

        if (docente == null) {
            model.addAttribute("error", "No existe ninguna cuenta con ese email.");
            return "auth/forgot-password";
        }

        // Borro cualquier token previo de este docente para evitar la unique constraint sobre docente_id.
        // De este modo cada solicitud de recuperación reemplaza siempre la anterior.
        tokenRepo.deleteByDocente(docente);
        tokenRepo.flush();

        // Genero un UUID aleatorio para usarlo como token de un solo uso
        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken(token, docente);
        tokenRepo.save(myToken);

        // Construyo el enlace de recuperación usando la URL publica configurada (no hardcoded localhost).
        String resetLink = baseUrl + "/auth/reset-password?token=" + token;

        // Intento enviar el correo electrónico con el enlace de recuperación.
        // El try-catch evita que la aplicación se detenga si el servidor SMTP falla.
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(docente.getEmail());
                message.setSubject("Recuperación de contraseña — GestiónDocentes");
                message.setText("Hola " + docente.getNombre() + ",\n\n"
                        + "Has solicitado restablecer la contraseña de tu cuenta en GestiónDocentes.\n"
                        + "Haz clic en el siguiente enlace para establecer una nueva contraseña:\n\n"
                        + resetLink + "\n\n"
                        + "Este enlace caducará en 60 minutos. Si no has solicitado este cambio, ignora este mensaje.\n\n"
                        + "Un saludo,\nEquipo de GestiónDocentes");
                mailSender.send(message);
                log.info("Email de recuperación enviado correctamente a {}", email);
            } catch (Exception e) {
                log.warn("No se pudo enviar email de recuperación a {}: {}", email, e.getMessage());
                log.info("Link de recuperación (fallback): {}", resetLink);
            }
        } else {
            log.info("Mail sender no configurado. Link de recuperación: {}", resetLink);
        }

        model.addAttribute("message", "Se ha enviado un enlace a tu correo.");
        return "auth/forgot-password";
    }

    // Vista para establecer la nueva contraseña. Valido que el token exista y no haya expirado.
    @GetMapping("/auth/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        PasswordResetToken passToken = tokenRepo.findByToken(token);

        if (passToken == null || passToken.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "El token es inválido o ha expirado.");
            return "auth/login";
        }

        model.addAttribute("token", token);
        return "auth/change-password";
    }

    // Guardo la nueva contraseña encriptada y elimino el token para que no pueda reutilizarse.
    @PostMapping("/auth/reset-password")
    public String saveNewPassword(@RequestParam("token") String token, @RequestParam("password") String password) {
        PasswordResetToken passToken = tokenRepo.findByToken(token);

        if (passToken != null) {
            Docente docente = passToken.getDocente();
            // Es fundamental encriptar la contraseña antes de persistirla en la base de datos
            docente.setPassword(passwordEncoder.encode(password));
            // Marco que el usuario ya ha realizado el cambio de contraseña obligatorio
            docente.setPasswordChanged(true);
            docenteRepo.save(docente);

            // Elimino el token de la base de datos por seguridad
            tokenRepo.delete(passToken);
        }
        return "redirect:/login?resetSuccess";
    }
}
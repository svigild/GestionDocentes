package com.sergiovd.gestiondocentes.controller;

import com.sergiovd.gestiondocentes.model.Docente;
import com.sergiovd.gestiondocentes.model.PasswordResetToken;
import com.sergiovd.gestiondocentes.repository.DocenteRepository;
import com.sergiovd.gestiondocentes.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private JavaMailSender mailSender;
    @Autowired private PasswordEncoder passwordEncoder;

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
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        Docente docente = docenteRepo.findDocenteByEmail(email).orElse(null);

        if (docente == null) {
            model.addAttribute("error", "No existe ninguna cuenta con ese email.");
            return "auth/forgot-password";
        }

        // Genero un UUID aleatorio para usarlo como token de un solo uso
        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken(token, docente);
        tokenRepo.save(myToken);

        // Intento enviar el correo electrónico con el enlace de recuperación.
        // He incluido un bloque try-catch para que, si el servidor SMTP falla (o no está configurado en desarrollo),
        // la aplicación no se detenga y pueda ver el link en la consola para testear.
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(docente.getEmail());
            message.setSubject("Recuperación de Contraseña - GestionDocentes");
            message.setText("Para cambiar tu contraseña, haz clic aquí: " +
                    "http://localhost:8080/auth/reset-password?token=" + token);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar email de recuperación a {}: {}", email, e.getMessage());
            log.info("Link de recuperación (desarrollo): http://localhost:8080/auth/reset-password?token={}", token);
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
package com.sergiovd.gestiondocentes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de envío de correos electrónicos.
 *
 * Soporta dos modos según la configuración:
 *  - Brevo API HTTP (recomendado en producción / hosting tipo Render que bloquea SMTP):
 *      se activa cuando la propiedad app.mail.brevo-api-key está definida.
 *  - SMTP clásico (Mailtrap, servidor local, etc.) como fallback de desarrollo.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.from:no-reply@gestiondocentes.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:GestiónDocentes}")
    private String mailFromName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envía un correo electrónico. Devuelve true si se envía con éxito, false si falla.
     * No lanza excepciones para no interrumpir flujos de negocio.
     */
    public boolean send(String toEmail, String toName, String subject, String body) {
        // 1) Si hay API key de Brevo, uso la API HTTP (puerto 443, no bloqueado por Render)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            return enviarConBrevoApi(toEmail, toName, subject, body);
        }

        // 2) Fallback: SMTP clásico (solo útil en desarrollo local con Mailtrap o similar)
        if (mailSender != null) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(toEmail);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
                log.info("Email enviado por SMTP a {}", toEmail);
                return true;
            } catch (Exception e) {
                log.warn("Fallo SMTP al enviar a {}: {}", toEmail, e.getMessage());
                return false;
            }
        }

        log.warn("Mail service no configurado: no se envió email a {}", toEmail);
        return false;
    }

    private boolean enviarConBrevoApi(String toEmail, String toName, String subject, String body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            Map<String, Object> sender = new HashMap<>();
            sender.put("name", mailFromName);
            sender.put("email", mailFrom);

            Map<String, Object> destinatario = new HashMap<>();
            destinatario.put("email", toEmail);
            if (toName != null && !toName.isBlank()) {
                destinatario.put("name", toName);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("sender", sender);
            payload.put("to", List.of(destinatario));
            payload.put("subject", subject);
            payload.put("textContent", body);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("Email enviado por Brevo API a {}", toEmail);
            return true;
        } catch (Exception e) {
            log.warn("Fallo al enviar email vía Brevo API a {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}

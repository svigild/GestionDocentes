package com.sergiovd.gestiondocentes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gestión de Docentes")
                        .version("1.0.0")
                        .description("API REST para la gestión de docentes, departamentos, horarios y solicitudes del centro educativo.")
                        .contact(new Contact()
                                .name("Sergio Vigil Díaz")
                                .email("svigild@iesribera.com")));
    }
}

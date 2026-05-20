# GestionDocentes - Imagen Docker

Aplicación web de gestión integral para centros de formación profesional, desarrollada con **Spring Boot 3.3.2** y **Java 21**.

## Descripción

GestionDocentes permite gestionar docentes, horarios, guardias, solicitudes de permisos y faltas de un centro educativo. Incluye sistema de autenticación con roles (Dirección, Jefatura, Profesor), carga masiva desde CSV y algoritmo inteligente de asignación de guardias.

## Requisitos

- **Base de datos MySQL 8.0** accesible desde el contenedor
- Puerto `8080` disponible (configurable con la variable `PORT`)

## Uso rápido con Docker Compose

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: gestion_docentes
      MYSQL_USER: appuser
      MYSQL_PASSWORD: apppass
    volumes:
      - mysql_data:/var/lib/mysql

  app:
    image: serxu/gestion-docentes:latest
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/gestion_docentes?allowPublicKeyRetrieval=TRUE&useSSL=FALSE&serverTimezone=Europe/Madrid
      DB_USERNAME: appuser
      DB_PASSWORD: apppass
    depends_on:
      - mysql

volumes:
  mysql_data:
```

```bash
docker compose up -d
# Acceder a http://localhost:8080
```

## Uso con Docker Run

```bash
docker run -d \
  --name gestiondocentes \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://tu-mysql-host:3306/gestion_docentes?allowPublicKeyRetrieval=TRUE&useSSL=FALSE" \
  -e DB_USERNAME=usuario \
  -e DB_PASSWORD=contraseña \
  serxu/gestion-docentes:latest
```

## Variables de entorno

| Variable | Obligatoria | Descripción | Valor por defecto |
|---|---|---|---|
| `DB_URL` | Sí | URL JDBC de conexión a MySQL | `jdbc:mysql://localhost:3306/gestion_docentes?...` |
| `DB_USERNAME` | Sí | Usuario de la base de datos | `root` |
| `DB_PASSWORD` | Sí | Contraseña de la base de datos | `root` |
| `PORT` | No | Puerto del servidor | `8080` |
| `SHOW_SQL` | No | Mostrar queries SQL en logs | `true` |
| `JAVA_OPTS` | No | Opciones de la JVM | `-Xmx512m -Xms256m` |
| `MAIL_HOST` | No | Host del servidor SMTP | `localhost` |
| `MAIL_PORT` | No | Puerto SMTP | `2525` |

## Credenciales por defecto

Al iniciar por primera vez (base de datos vacía), se crea automáticamente:

- **Usuario:** `admin@educastur.org`
- **Contraseña:** `1234`
- **Rol:** Dirección (acceso completo)

## Tags disponibles

- `latest` — Última versión estable
- `1.0.0` — Primera versión de producción

## Stack tecnológico

- Java 21 (Eclipse Temurin JRE)
- Spring Boot 3.3.2
- Thymeleaf + Bootstrap 5
- Spring Security
- Spring Data JPA / Hibernate
- MySQL 8.0

## Autor

Sergio Vigil Díaz — Proyecto Intermodular DAW/DAM

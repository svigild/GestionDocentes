# GestiónDocentes

Aplicación web para la gestión integral del profesorado de un centro de Formación Profesional: claustro, horarios, guardias, solicitudes de asuntos propios y faltas.

**Alumno:** Sergio Vigil Díaz
**Curso:** 2.º DAW · 2025/2026
**Proyecto Intermodular** 

---

## Tecnologías

- **Java 21** + **Spring Boot 3.3.2** (Spring MVC, Spring Security, Spring Data JPA)
- **Thymeleaf** + **Bootstrap 5** (vistas web responsivas)
- **MySQL 8** (desarrollo) / **PostgreSQL 16** (producción)
- **Docker** + **Docker Compose** (containerización)
- **Springdoc OpenAPI** (documentación de la API REST)

---

## Arranque rápido

### Opción A — Aplicación ya desplegada

La aplicación está disponible en:
**https://gestion-docentes-latest.onrender.com**

> ⚠️ El servicio de hosting puede tardar ~30 segundos en arrancar la primera vez tras un periodo de inactividad.

### Opción B — Con Docker Compose (recomendado)

Requiere tener instalado [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```bash
git clone https://github.com/svigild/GestionDocentes.git
cd GestionDocentes
docker-compose up -d
```

La aplicación estará disponible en `http://localhost:8080`.

### Opción C — Local con Maven

Requiere **Java 21** y **MySQL 8** instalados.

1. Crear una base de datos llamada `gestion_docentes` en MySQL.
2. Ajustar usuario y contraseña en `src/main/resources/application.properties`.
3. Ejecutar:

```bash
./mvnw spring-boot:run
```

La aplicación estará disponible en `http://localhost:8080`.

---

## Credenciales de acceso

Al arrancar por primera vez se cargan automáticamente los usuarios desde `docentes.csv`. La contraseña inicial de todos los usuarios es `1234`.

| Rol | Email | Cambio obligatorio al primer acceso |
|---|---|---|
| Dirección | admin@educastur.org | No |
| Jefatura | maria@educastur.org | Sí |
| Profesor | sergio@educastur.org | Sí |
| Profesor | laura@educastur.org | Sí |
| Profesor | david@educastur.org | Sí |

---

## Estructura del proyecto

```
src/main/java/com/sergiovd/gestiondocentes/
├── config/         # SecurityConfig, GlobalExceptionHandler, AdminLoader, CsvDataLoader
├── controller/     # Controladores web + API REST (/api/**)
├── dto/            # Objetos de transferencia
├── model/          # Entidades JPA (9 entidades)
├── repository/     # Interfaces JpaRepository (9 repositorios)
└── service/        # Lógica de negocio (DocenteService, GuardiaService, ...)
```

---

## Documentación de la API REST

Una vez arrancada la aplicación, la documentación interactiva (Swagger UI) está disponible en:

```
http://localhost:8080/swagger-ui.html
```

---

## Tests

```bash
./mvnw test
```

Incluye tests unitarios con **Mockito** y **AssertJ** para el algoritmo de guardias y el mapeo de roles.

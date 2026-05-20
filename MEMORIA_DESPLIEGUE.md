# Memoria de Despliegue — GestionDocentes

> **Autor:** Sergio Vigil Díaz  
> **Fecha:** Mayo 2026  
> **Módulo:** Despliegue de Aplicaciones Web (Unidades 3 y 5)

---

## Índice

1. [Descripción del Proyecto](#1-descripción-del-proyecto)
2. [Arquitectura y Componentes](#2-arquitectura-y-componentes)
3. [Proceso de Dockerización](#3-proceso-de-dockerización)
4. [Persistencia de Datos](#4-persistencia-de-datos)
5. [Publicación en Docker Hub](#5-publicación-en-docker-hub)
6. [Despliegue en Hosting (Render)](#6-despliegue-en-hosting-render)
7. [Problemas Encontrados y Soluciones](#7-problemas-encontrados-y-soluciones)
8. [Instrucciones para Reproducir el Despliegue](#8-instrucciones-para-reproducir-el-despliegue)
9. [Enlaces](#9-enlaces)

---

## 1. Descripción del Proyecto

**GestionDocentes** es una aplicación web desarrollada con **Spring Boot** para la gestión integral de un centro de formación profesional. Permite administrar docentes, horarios, guardias, solicitudes de permisos y faltas.

### Funcionalidades principales

| Funcionalidad | Descripción |
|---|---|
| **Gestión de Docentes** | Alta, baja, modificación y consulta de profesorado |
| **Horarios** | Asignación de franjas horarias a docentes por día/hora/asignatura |
| **Guardias** | Algoritmo de 3 pasos para asignar sustitutos a docentes ausentes |
| **Solicitudes** | Peticiones de permisos con flujo de aprobación y subida de material |
| **Faltas** | Registro y seguimiento de ausencias con estado de guardia |
| **Seguridad** | Login con roles (Dirección, Jefatura, Profesor) y cambio obligatorio de contraseña |
| **Carga CSV** | Importación masiva de docentes y horarios desde ficheros CSV |

---

## 2. Arquitectura y Componentes

La aplicación está compuesta por **tres componentes** principales desplegados en contenedores Docker:

```
┌─────────────────────────────────────────────┐
│                  USUARIO                     │
│              (Navegador Web)                 │
└──────────────────┬──────────────────────────┘
                   │ HTTP (puerto 8080)
                   ▼
┌─────────────────────────────────────────────┐
│        CONTENEDOR: gestiondocentes-app       │
│  ┌───────────────────────────────────────┐  │
│  │     Spring Boot 3.3.2 + Java 21       │  │
│  │  • Thymeleaf (vistas HTML)            │  │
│  │  • Spring Security (autenticación)    │  │
│  │  • Spring Data JPA (acceso a datos)   │  │
│  │  • API REST + Swagger UI              │  │
│  └──────────────────┬────────────────────┘  │
└─────────────────────┼───────────────────────┘
                      │ JDBC (puerto 3306)
                      ▼
┌─────────────────────────────────────────────┐
│         CONTENEDOR: gestiondocentes-db       │
│  ┌───────────────────────────────────────┐  │
│  │           MySQL 8.0                    │  │
│  │  • Base de datos: gestion_docentes    │  │
│  │  • Volumen: mysql_data                │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### Stack tecnológico

| Componente | Tecnología | Versión |
|---|---|---|
| Backend | Spring Boot | 3.3.2 |
| Lenguaje | Java | 21 |
| Motor de plantillas | Thymeleaf | 3.x |
| Base de datos | MySQL | 8.0 |
| ORM | Hibernate (JPA) | 6.5.2 |
| Seguridad | Spring Security | 6.x |
| Frontend | Bootstrap | 5.3 |
| Contenedores | Docker | 27.x |
| Orquestación | Docker Compose | 2.29 |

---

## 3. Proceso de Dockerización

### 3.1. Dockerfile (Multi-stage Build)

Se utiliza un **Dockerfile multi-stage** para minimizar el tamaño de la imagen final y reducir el número de capas:

```dockerfile
# ============================================================
# ETAPA 1: Compilación (build stage)
# ============================================================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Cacheo de dependencias (capa separada)
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw dependency:resolve -B

# Compilación del código fuente
COPY src src
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar target/app.jar

# ============================================================
# ETAPA 2: Ejecución (runtime stage)
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario no-root por seguridad
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=build /app/target/app.jar app.jar

RUN mkdir -p /app/uploads && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/login || exit 1

ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]
```

**Optimizaciones aplicadas:**

- **Multi-stage build**: La imagen final solo contiene el JRE + JAR, sin JDK ni Maven (~704 MB vs ~1.5 GB)
- **Cacheo de dependencias**: Se copian `pom.xml` y wrapper antes del código fuente. Si no cambian las dependencias, Docker reutiliza la capa cacheada.
- **Usuario no-root**: El contenedor se ejecuta como `appuser`, no como `root`.
- **Health check**: Verifica automáticamente que la app responde en `/login`.
- **Variables de entorno**: `JAVA_OPTS` y `PORT` son configurables sin reconstruir la imagen.

### 3.2. .dockerignore

Se utiliza un fichero `.dockerignore` para excluir archivos innecesarios del contexto de build:

```
target/
.idea/
*.iml
.git/
Dockerfile
compose.yaml
*.md
*.log
```

### 3.3. Docker Compose (compose.yaml)

El fichero `compose.yaml` orquesta los dos servicios:

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: gestiondocentes-db
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-rootpass}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-gestion_docentes}
      MYSQL_USER: ${MYSQL_USER:-appuser}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-apppass}
    ports:
      - "3307:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - gestiondocentes-net
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  app:
    build:
      context: .
      dockerfile: Dockerfile
    image: serxu/gestion-docentes:latest
    container_name: gestiondocentes-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/gestion_docentes?allowPublicKeyRetrieval=TRUE&useSSL=FALSE&serverTimezone=Europe/Madrid
      DB_USERNAME: ${MYSQL_USER:-appuser}
      DB_PASSWORD: ${MYSQL_PASSWORD:-apppass}
      SHOW_SQL: "false"
      PORT: 8080
    volumes:
      - uploads_data:/app/uploads
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - gestiondocentes-net

volumes:
  mysql_data:
    name: gestiondocentes-mysql-data
  uploads_data:
    name: gestiondocentes-uploads

networks:
  gestiondocentes-net:
    name: gestiondocentes-network
```

### 3.4. Comandos utilizados

```bash
# Construir y levantar todos los contenedores
docker-compose up --build -d

# Ver el estado de los contenedores
docker ps

# Ver logs de la aplicación
docker-compose logs -f app

# Parar todos los contenedores
docker-compose down

# Parar y eliminar volúmenes (CUIDADO: borra datos)
docker-compose down -v
```

---

## 4. Persistencia de Datos

La persistencia se garantiza mediante **dos mecanismos**:

### 4.1. Volumen para MySQL

```yaml
volumes:
  mysql_data:
    name: gestiondocentes-mysql-data
```

Este volumen nombrado almacena los ficheros de datos de MySQL (`/var/lib/mysql`). Los datos **sobreviven** a reinicios y recreaciones de contenedores.

### 4.2. Volumen para archivos subidos

```yaml
volumes:
  uploads_data:
    name: gestiondocentes-uploads
```

Los docentes pueden subir material justificativo en sus solicitudes. Estos archivos se almacenan en `/app/uploads` dentro del contenedor, respaldado por un volumen persistente.

### 4.3. Verificación de persistencia

```bash
# Verificar que los volúmenes existen
docker volume ls | grep gestiondocentes

# Inspeccionar un volumen
docker volume inspect gestiondocentes-mysql-data
```

| Volumen | Ruta en contenedor | Propósito |
|---|---|---|
| `gestiondocentes-mysql-data` | `/var/lib/mysql` | Datos de la base de datos MySQL |
| `gestiondocentes-uploads` | `/app/uploads` | Archivos subidos por docentes |

---

## 5. Publicación en Docker Hub

### 5.1. Datos de la imagen

| Campo | Valor |
|---|---|
| **Repositorio** | `serxu/gestion-docentes` |
| **Tags** | `latest`, `1.0.0` |
| **Visibilidad** | Pública |
| **URL** | [hub.docker.com/r/serxu/gestion-docentes](https://hub.docker.com/r/serxu/gestion-docentes) |

### 5.2. Comandos de publicación

```bash
# Etiquetar la imagen con versión
docker tag serxu/gestion-docentes:latest serxu/gestion-docentes:1.0.0

# Autenticarse en Docker Hub
docker login -u serxu

# Subir ambas etiquetas
docker push serxu/gestion-docentes:1.0.0
docker push serxu/gestion-docentes:latest
```

### 5.3. Uso de la imagen

```bash
# Descargar y ejecutar directamente (requiere MySQL accesible)
docker pull serxu/gestion-docentes:latest

docker run -d \
  --name gestiondocentes \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host:3306/gestion_docentes?allowPublicKeyRetrieval=TRUE&useSSL=FALSE" \
  -e DB_USERNAME=usuario \
  -e DB_PASSWORD=contraseña \
  serxu/gestion-docentes:latest
```

---

## 6. Despliegue en Hosting (Render)

### 6.1. Configuración en Render

El despliegue se realizó en **Render** (https://render.com), utilizando:

- **Web Service**: Despliega la imagen Docker desde Docker Hub
- **PostgreSQL**: Base de datos gestionada por Render (plan gratuito)

> **Nota:** Render no ofrece MySQL de forma nativa, por lo que se utiliza PostgreSQL. Gracias a JPA/Hibernate, la aplicación es compatible con ambos motores sin cambios en el código — solo cambia la URL de conexión y el driver JDBC.

### 6.2. Pasos del despliegue

1. **Crear base de datos PostgreSQL** en Render:
   - Ir a Dashboard → New + → PostgreSQL
   - Configurar nombre: `gestiondocentes-db`
   - Plan: Free
   - Crear y copiar la **Internal Database URL**

2. **Crear Web Service**:
   - Ir a Dashboard → New + → Web Service
   - Seleccionar "Deploy an existing image from a registry"
   - Image URL: `docker.io/serxu/gestion-docentes:latest`
   - Plan: Free
   - Configurar las variables de entorno:

| Variable | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host-render>:5432/<nombre-bd>` |
| `DB_USERNAME` | *(usuario proporcionado por Render)* |
| `DB_PASSWORD` | *(contraseña proporcionada por Render)* |
| `PORT` | `8080` |
| `SHOW_SQL` | `false` |

   > La Internal Database URL de Render tiene formato `postgres://user:pass@host/db`.
   > Se convierte a JDBC: `jdbc:postgresql://host:5432/db`

3. **Desplegar** y esperar a que el health check confirme que la app está lista.

### 6.3. Credenciales de acceso

Una vez desplegada, la app crea automáticamente un usuario administrador:

| Campo | Valor |
|---|---|
| **Email** | `admin@educastur.org` |
| **Contraseña** | `1234` |
| **Rol** | Dirección (acceso completo) |

---

## 7. Problemas Encontrados y Soluciones

### Problema 1: Puerto 8080 ya en uso

**Descripción:** Al intentar levantar el contenedor de la app, el puerto 8080 estaba ocupado por una instancia local de la aplicación.

**Solución:** Se detuvo el proceso Java local antes de ejecutar `docker-compose up`. También se configuró el puerto del contenedor MySQL en 3307 para evitar colisiones con el MySQL local (puerto 3306).

```bash
# Identificar proceso en el puerto
netstat -ano | findstr :8080

# Detener el proceso
taskkill /PID <pid> /F
```

### Problema 2: Conexión de la app a MySQL en Docker

**Descripción:** La app no podía conectarse a MySQL porque usaba `localhost` como host de base de datos, que dentro de un contenedor apunta al propio contenedor, no al de MySQL.

**Solución:** Se configuró la URL de conexión usando el nombre del servicio Docker (`mysql`) como host, que Docker Compose resuelve internamente a la IP del contenedor de MySQL. Además, se usó `depends_on` con `condition: service_healthy` para que la app espere a que MySQL esté listo.

### Problema 3: Configuración hardcodeada

**Descripción:** Los valores de conexión a base de datos estaban fijos en `application.properties`, impidiendo su uso en diferentes entornos (local, Docker, Render).

**Solución:** Se externalizaron todas las configuraciones sensibles mediante variables de entorno con valores por defecto:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/gestion_docentes?...}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}
server.port=${PORT:8080}
```

### Problema 4: Render no ofrece MySQL

**Descripción:** La aplicación fue desarrollada con MySQL, pero Render solo ofrece PostgreSQL como base de datos relacional gratuita.

**Solución:** Se añadió el driver de PostgreSQL al `pom.xml` y se eliminó la configuración hardcodeada del dialecto de Hibernate. Al usar JPA/Hibernate como capa de abstracción, la aplicación funciona tanto con MySQL (desarrollo local / Docker Compose) como con PostgreSQL (Render) sin cambiar una sola línea de código — solo la URL JDBC.

```xml
<!-- PostgreSQL para despliegue en Render -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Problema 5: Login en Docker Hub con cuenta Google

**Descripción:** Al intentar `docker login`, no funcionaba porque la cuenta de Docker Hub fue creada con Google SSO (sin contraseña).

**Solución:** Se generó un **Personal Access Token** desde Docker Hub → Settings → Security → New Access Token, y se usó ese token como contraseña en `docker login`.

---

## 8. Instrucciones para Reproducir el Despliegue

### Requisitos previos

- [Docker](https://docs.docker.com/get-docker/) (v20+)
- [Docker Compose](https://docs.docker.com/compose/install/) (v2+)
- Git (opcional, para clonar el repositorio)

### Opción A: Usando Docker Compose (recomendado)

```bash
# 1. Clonar el repositorio
git clone <url-del-repositorio>
cd "Proyecto Intermodular - FINAL"

# 2. Levantar todos los servicios
docker-compose up -d

# 3. Verificar que están corriendo
docker ps

# 4. Acceder a la aplicación
# Abrir http://localhost:8080 en el navegador
# Usuario: admin@educastur.org | Contraseña: 1234
```

### Opción B: Usando la imagen de Docker Hub

```bash
# 1. Crear una red Docker
docker network create gestiondocentes-network

# 2. Levantar MySQL
docker run -d \
  --name gestiondocentes-db \
  --network gestiondocentes-network \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -e MYSQL_DATABASE=gestion_docentes \
  -e MYSQL_USER=appuser \
  -e MYSQL_PASSWORD=apppass \
  -v mysql_data:/var/lib/mysql \
  mysql:8.0

# 3. Esperar 30 segundos a que MySQL arranque, luego levantar la app
docker run -d \
  --name gestiondocentes-app \
  --network gestiondocentes-network \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://gestiondocentes-db:3306/gestion_docentes?allowPublicKeyRetrieval=TRUE&useSSL=FALSE&serverTimezone=Europe/Madrid" \
  -e DB_USERNAME=appuser \
  -e DB_PASSWORD=apppass \
  serxu/gestion-docentes:latest

# 4. Acceder a http://localhost:8080
```

### Opción C: Desplegar en Render

1. Crear una base de datos MySQL en Render
2. Crear un Web Service con la imagen `serxu/gestion-docentes:latest`
3. Configurar las variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PORT`)
4. Desplegar

---

## 9. Enlaces

| Recurso | URL |
|---|---|
| **Sitio web desplegado** | [https://gestion-docentes-latest.onrender.com](https://gestion-docentes-latest.onrender.com) |
| **Imagen en Docker Hub** | [hub.docker.com/r/serxu/gestion-docentes](https://hub.docker.com/r/serxu/gestion-docentes) |
| **Repositorio del proyecto** | *(URL del repositorio Git)* |

---

### Variables de entorno disponibles

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de conexión a MySQL | `jdbc:mysql://localhost:3306/gestion_docentes?...` |
| `DB_USERNAME` | Usuario de base de datos | `root` |
| `DB_PASSWORD` | Contraseña de base de datos | `root` |
| `PORT` | Puerto del servidor web | `8080` |
| `SHOW_SQL` | Mostrar consultas SQL en log | `true` |
| `MAIL_HOST` | Servidor SMTP | `localhost` |
| `MAIL_PORT` | Puerto SMTP | `2525` |
| `MAX_PERMISOS_DIARIOS` | Máximo de permisos por día | `3` |
| `JAVA_OPTS` | Opciones de la JVM | `-Xmx512m -Xms256m` |

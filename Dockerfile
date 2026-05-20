# ============================================================
# ETAPA 1: Compilación (build stage)
# Usa una imagen con Maven y JDK 21 para compilar el proyecto.
# Esta etapa NO se incluye en la imagen final → imagen más ligera.
# ============================================================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copiamos primero solo los ficheros de dependencias (pom.xml + wrapper).
# Docker cachea esta capa: si no cambian las dependencias, no se re-descargan.
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Damos permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw

# Descargamos las dependencias en una capa separada (caché eficiente)
RUN ./mvnw dependency:resolve -B

# Ahora copiamos el código fuente (esto invalida la caché solo si cambia el código)
COPY src src

# Compilamos el proyecto sin ejecutar los tests (ya están validados)
# -B = modo batch (sin interactividad)
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar target/app.jar

# ============================================================
# ETAPA 2: Ejecución (runtime stage)
# Usa solo un JRE ligero → imagen final mucho más pequeña.
# ============================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Creamos un usuario no-root por seguridad
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copiamos SOLO el JAR compilado desde la etapa anterior
COPY --from=build /app/target/app.jar app.jar

# Directorio para archivos subidos (persistencia)
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app

USER appuser

# Puerto por defecto de Spring Boot (configurable con PORT)
EXPOSE 8080

# Health check para verificar que la app responde
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/login || exit 1

# Variables de entorno con valores por defecto
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    PORT=8080

# Ejecutamos la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]

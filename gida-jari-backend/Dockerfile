# Gida Jari - backend Spring Boot (Java 17)
# Etape 1 : compilation du jar avec Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Etape 2 : image d'execution legere
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 gidajari && mkdir -p /app/uploads && chown gidajari /app/uploads
COPY --from=build /build/target/gida-jari-backend-*.jar /app/app.jar
USER gidajari
EXPOSE 8080
# Les fichiers televerses vont dans /app/uploads (volume Docker, voir deploy/docker-compose.yml)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

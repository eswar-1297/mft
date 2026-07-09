# Multi-stage build: compile with Maven, then ship a slim JRE image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first for faster rebuilds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Run as an unprivileged user — never as root.
RUN addgroup -S mft && adduser -S mft -G mft
COPY --from=build /app/target/mft-0.1.0.jar app.jar
USER mft
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

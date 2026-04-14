# Multi-stage build.
# Stage 1 compiles the jar; stage 2 runs it on a slim JRE image.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Pre-cache deps
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline -B

# Build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime image — much smaller than the build image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Railway injects PORT; default to 8080 locally
ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]

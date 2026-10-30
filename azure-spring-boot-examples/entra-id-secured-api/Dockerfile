# Build and run on real Java 8 (1.8) without installing anything locally.
#
#   docker build -t entra-id-secured-api .
#   docker run --rm -p 8080:8080 --env-file .env entra-id-secured-api

FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /src/target/entra-id-secured-api-*.jar app.jar
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

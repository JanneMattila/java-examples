# Multi-stage build for Databricks Connect Java application
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/databricks-connect-example-*.jar /app/app.jar

# Copy data files if needed
COPY data ./data

# Set Java options for Spark compatibility
ENV JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED \
               --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
               --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
               --add-opens=java.base/java.io=ALL-UNNAMED \
               --add-opens=java.base/java.net=ALL-UNNAMED \
               --add-opens=java.base/java.nio=ALL-UNNAMED \
               --add-opens=java.base/java.util=ALL-UNNAMED \
               --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
               --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
               --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
               --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
               --add-opens=java.base/sun.security.action=ALL-UNNAMED \
               --add-opens=java.base/sun.util.calendar=ALL-UNNAMED"

# Default command runs Word Count demo
# Override with docker run arguments to run Sales Analytics demo
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app/app.jar com.example.databricks.WordCountAppDatabricks \"$@\"", "--"]

# To run Sales Analytics demo instead:
# docker run ... com.example.databricks.SalesAnalyticsDemo [file-path]

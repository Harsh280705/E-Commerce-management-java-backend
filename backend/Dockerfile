FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml ./
RUN mvn -q dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/level3-ecommerce-3.0.0.jar app.jar
COPY docker-entrypoint.sh ./
RUN chmod +x docker-entrypoint.sh

EXPOSE 8080

# Entrypoint waits for infra, then runs the API (seeding is done in-app when enabled).
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]

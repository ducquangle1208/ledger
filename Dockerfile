FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ledger && adduser -S ledger -G ledger
WORKDIR /app
COPY --from=build /workspace/target/mini_ledger-*.jar app.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

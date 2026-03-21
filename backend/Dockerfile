FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared-contract ./shared-contract
COPY backend ./backend

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon :backend:buildFatJar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

COPY --from=builder /workspace/backend/build/libs/family-messenger-backend-all.jar /app/backend.jar

EXPOSE 8080

ENV PORT=8080

ENTRYPOINT ["java", "-jar", "/app/backend.jar"]

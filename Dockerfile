# ── Backend Build ─────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS backend-builder
WORKDIR /app/Backend/transactions

COPY Backend/transactions/pom.xml ./
RUN mvn -B dependency:go-offline

COPY Backend/transactions/src ./src
RUN mvn -B clean package -DskipTests

# ── Backend Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS backend
WORKDIR /app

RUN addgroup -S tmsgroup && adduser -S tmsuser -G tmsgroup
COPY --from=backend-builder /app/Backend/transactions/target/transactions-*.jar app.jar
RUN chown tmsuser:tmsgroup app.jar

USER tmsuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/rules >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

# ── Frontend Runtime ──────────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS frontend
COPY Frontend/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:80/index.html >/dev/null || exit 1
CMD ["nginx", "-g", "daemon off;"]
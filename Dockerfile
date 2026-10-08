# --- 1. Angular frontend -> static files ---
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# angular.json writes the build into ../backend/src/main/resources/static
RUN mkdir -p ../backend/src/main/resources && npx ng build

# --- 2. Spring Boot backend (bundles the frontend build) ---
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /src/backend
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /src/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# --- 3. Runtime ---
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 leadlens && mkdir /app /app/data && chown -R leadlens /app
WORKDIR /app
COPY --from=backend /src/backend/target/leadlens.jar app.jar
USER leadlens
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

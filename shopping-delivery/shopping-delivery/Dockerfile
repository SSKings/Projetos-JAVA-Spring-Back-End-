# ---------- build ----------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/ecommerce-*.jar /app.jar

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-alpine
# usuário sem privilégios; o container não roda como root
RUN addgroup -S app && adduser -S app -G app && mkdir -p /var/log/shopeasy && chown app:app /var/log/shopeasy
USER app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/products?size=1 >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

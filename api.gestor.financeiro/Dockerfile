# Imagens fixadas por digest (revisão de segurança 3). A antiga openjdk:17-jdk-alpine foi descontinuada e não
# recebe correções; o runtime agora é só JRE (sem compilador dentro do container).
FROM maven:3.9-eclipse-temurin-17-alpine@sha256:b7c8d7298dbabbe750441736cd99a1d120a58083d5af8d2bd75a1624cfdd8f3a AS build
WORKDIR /build

COPY . .
RUN mvn -B -C clean package -DskipTests


FROM eclipse-temurin:17-jre-alpine@sha256:e2ba4e947bd5e236a9866fbdda03e4936036cb9e9d16ea0d84d103d6634a5c21

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=build --chown=app:app /build/target/*.jar ./application.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]

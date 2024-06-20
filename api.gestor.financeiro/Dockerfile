FROM openjdk:17-jdk-alpine

RUN mkdir /app

RUN addgroup app && adduser app -S -G app app
USER app

WORKDIR  /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
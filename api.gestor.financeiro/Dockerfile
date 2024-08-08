FROM maven:3.8.5-openjdk-17 as build
WORKDIR /build

COPY . .
RUN mvn clean package -DskipTests


FROM openjdk:17-jdk-alpine

RUN mkdir /app

RUN addgroup app && adduser app -S -G app app
USER app

WORKDIR  /app

COPY --from=build ./build/target/*.jar ./application.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
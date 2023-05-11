# Указываем базовый образ
FROM openjdk:17

WORKDIR /app

COPY target/WorkoutRunBot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8888

CMD ["java", "-jar", "app.jar"]

# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies
RUN mvn dependency:go-offline -B
COPY src ./src
# Build package without running tests
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Create directories for static uploads as referenced in application.properties
RUN mkdir -p static/uploads/resumes static/uploads/assessment-files static/uploads/images
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

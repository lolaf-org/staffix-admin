# Built from target/ after ./mvnw install
FROM eclipse-temurin:21-jre
RUN useradd --system staffix
COPY target/staffix-admin-simulator.jar /opt/staffix-admin-simulator.jar
USER staffix
EXPOSE 8687 8688
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/staffix-admin-simulator.jar"]

# Built from target/ after ./mvnw install. Mount config/ with your users or JAAS module; /data keeps the connectors
FROM eclipse-temurin:21-jre
RUN useradd --system --home-dir /opt/staffix-admin staffix && mkdir /data && chown staffix /data
COPY target/staffix-admin/staffix-admin /opt/staffix-admin
USER staffix
WORKDIR /opt/staffix-admin
EXPOSE 8080
VOLUME /data
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar staffix-admin.jar --staffix.admin.data-dir=/data"]

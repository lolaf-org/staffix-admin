# Built from target/ after ./mvnw install; the data volume is seeded with connectors reaching the simulator container
FROM eclipse-temurin:21-jre
RUN useradd --system --home-dir /opt/staffix-admin staffix && mkdir /data && chown staffix /data
COPY target/staffix-admin-demo/staffix-admin /opt/staffix-admin
COPY src/docker/console.yaml /opt/staffix-admin/seed/console.yaml
USER staffix
WORKDIR /opt/staffix-admin
EXPOSE 8080
VOLUME /data
ENTRYPOINT ["sh", "-c", "[ -f /data/console.yaml ] || cp seed/console.yaml /data/; exec java $JAVA_OPTS -jar staffix-admin.jar --staffix.admin.data-dir=/data"]

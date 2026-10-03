FROM eclipse-temurin:21-jre
RUN useradd --system --home-dir /opt/staffix-admin staffix && mkdir /data && chown staffix /data
COPY console/staffix-admin /opt/staffix-admin
COPY connectors.yaml /opt/staffix-admin/seed/connectors.yaml
USER staffix
WORKDIR /opt/staffix-admin
EXPOSE 8080
VOLUME /data
ENTRYPOINT ["sh", "-c", "[ -f /data/connectors.yaml ] || cp seed/connectors.yaml /data/; exec java $JAVA_OPTS -jar staffix-admin.jar --staffix.admin.data-dir=/data"]

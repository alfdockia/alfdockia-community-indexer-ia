FROM alfresco/alfresco-base-java:jre17-rockylinux8-202306121108

ARG UID=10001
RUN yum install -y passwd && \
    adduser \
    --comment "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser && \
    passwd --lock appuser
USER appuser

WORKDIR /opt
ARG JAR_FILE=target/alfdockia-community-indexer-ia-1.0.0.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT exec java $JAVA_OPTS -jar app.jar

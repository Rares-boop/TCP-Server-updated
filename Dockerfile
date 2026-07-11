FROM eclipse-temurin:24-jdk

WORKDIR /app

COPY TCPServer.jar app.jar

RUN mkdir extracted && \
    cd extracted && \
    jar xf ../app.jar && \
    rm -f META-INF/*.SF META-INF/*.RSA META-INF/*.DSA && \
    jar cfm ../app.jar META-INF/MANIFEST.MF . && \
    cd .. && rm -rf extracted

EXPOSE 15555
EXPOSE 15556/udp
EXPOSE 15557/udp

CMD ["java", "-jar", "app.jar"]

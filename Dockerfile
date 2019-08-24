FROM openjdk:11-jdk as build

RUN mkdir /usr/local/SweetPotato
WORKDIR /usr/local/SweetPotato
ARG JAR_FILE
COPY target/${JAR_FILE} app.jar
COPY config config
RUN unzip app.jar > /dev/null; rm app.jar BOOT-INF/classes/application.yml BOOT-INF/classes/potatoes.xml BOOT-INF/lib/ojdbc8-12.2.0.1.jar BOOT-INF/lib/ucp-12.2.0.1.jar
COPY start.sh start.sh

FROM openjdk:11-jre
COPY --from=build /usr/local/SweetPotato /usr/local/SweetPotato
WORKDIR /usr/local/SweetPotato

EXPOSE 8080
CMD ["/bin/bash", "start.sh"]
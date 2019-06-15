FROM openjdk:11-jdk

RUN mkdir /usr/local/SweetPotato
WORKDIR /usr/local/SweetPotato
ARG JAR_FILE
COPY target/${JAR_FILE} app.jar
COPY config config
COPY start.sh start.sh

EXPOSE 8080
CMD ["/bin/bash", "start.sh"]
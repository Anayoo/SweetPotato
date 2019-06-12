FROM openjdk:11-jdk

RUN mkdir /usr/local/SweetPotato
ARG JAR_FILE
COPY target/${JAR_FILE} /usr/local/SweetPotato/app.jar
WORKDIR /usr/local/SweetPotato

EXPOSE 8060
CMD ["java", "-jar", "app.jar"]
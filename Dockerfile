FROM openjdk:8
COPY target/kafkus.jar /usr/lib/
CMD ["java", "-jar", "/usr/lib/kafkus.jar"]

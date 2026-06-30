FROM openjdk:17-alpine
WORKDIR /app
COPY ECC.java Blockchain.java Node.java ./
RUN javac ECC.java Blockchain.java Node.java
ENTRYPOINT ["java", "Node"]

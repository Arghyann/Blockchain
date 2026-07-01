FROM eclipse-temurin:17-alpine
WORKDIR /app
COPY ECC.java Blockchain.java Node.java MaliciousNode.java ./
RUN javac ECC.java Blockchain.java Node.java MaliciousNode.java
ENTRYPOINT ["java", "Node"]

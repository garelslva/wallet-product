FROM openjdk:17-jdk-alpine

# Cria o diretório para os logs do Garbage Collector
RUN mkdir -p /logs

WORKDIR /app

# Copia o arquivo .jar pré-compilado para o container
COPY target/*.jar app.jar

# Expõe a porta configurada (8073)
EXPOSE 8073

# Define o ENTRYPOINT com flags JVM otimizadas para performance máxima
ENTRYPOINT ["java", \
  "-XX:+UnlockExperimentalVMOptions", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=80.0", \
  "-XX:InitialRAMPercentage=40.0", \
  "-XX:+AlwaysPreTouch", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=100", \
  "-XX:+ParallelRefProcEnabled", \
  "-XX:+UseStringDeduplication", \
  "-XX:ActiveProcessorCount=2", \
  "-Xlog:gc*:file=/logs/gc.log:time,uptime,level,tags", \
  "-jar", "app.jar"]
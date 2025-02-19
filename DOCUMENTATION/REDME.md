# Wallet Service

Wallet Service is a digital wallet application developed with Java, Spring Boot, WebFlux, and reactive technologies. This document provides the necessary instructions to configure, compile, and run the application using Docker.

---

## Prerequisites

- **Docker**: Ensure Docker is installed and working.  
  [Download Docker](https://www.docker.com/get-started)

- **Maven**: Version: 3.9.9+ - Required to compile the application source code.  
  [Download Maven](https://maven.apache.org/install.html)

- **Java 17**: Required for compiling and running the application.  
  [Download Java](https://oracle.com/)

- **Postgres**: Relational database used for persistent data storage in the application.  
  [Download PostgreSQL](https://www.postgresql.org/download/)

- **Kafka**: Distributed streaming platform, essential for asynchronous communication between services and real-time processing.  
  [Download Kafka](https://kafka.apache.org/downloads)

- **Redis**: In-memory data store, used for caching and messaging, enhancing the application's performance.  
  [Download Redis](https://redis.io/download)

- **Prometheus**: Monitoring and alerting tool, used to collect and analyze real-time application metrics.  
  [Download Prometheus](https://prometheus.io/download/)

- **Logstash**: Tool for log collection, processing, and forwarding, integrated with the ELK stack for analysis and visualization.  
  [Download Logstash](https://www.elastic.co/logstash)

---

## Project Structure

- `src/`: Application source code.
- `pom.xml`: Maven configuration and project dependencies.
- `application.yaml`: Application settings (port, database, Redis, Kafka, etc.).
- `Dockerfile`: Docker image definition file (uses the already compiled `.jar` package).
---
# Getting Started

## Building the Application

Since packaging is **not performed inside the Docker image**, follow the steps below to compile the application and generate the `.jar` file:

1. **Compile the application**:
   ```bash
   mvn clean package -DskipTests

2. **Verify the generated file:**
   After execution, the compiled file will be available at:
   ```bash
   target/wallet-service-0.0.1-SNAPSHOT.jar

## Running the Application with Docker Compose

This application uses multiple services orchestrated via Docker Compose. Below is a brief description of each service that will be started, along with a step-by-step guide on how to execute the commands:

### Services and Their Functions

- **PostgreSQL Database (postgres)**  
  **Description:** Relational database responsible for storing the application's persistent data.  
  **Configuration:**
    - User: `wallet_user`
    - Password: `wallet_pass`
    - Database: `wallet`
    - Data persisted in the volume `postgres_data`.


- **Zookeeper (zookeeper)**  
  **Description:** Essential service for managing and coordinating the Kafka cluster, facilitating node discovery and managing the cluster state.  
  **Configuration:** Allows anonymous login, functioning as a dependency for Kafka.


- **Kafka (kafka)**  
  **Description:** Distributed message broker that enables asynchronous communication between microservices and real-time message processing.  
  **Configuration:**
    - Depends on Zookeeper.
    - Configures listeners and protocols to operate in plaintext mode.


- **Elasticsearch (elasticsearch)**  
  **Description:** Search and data analysis engine used to index, search, and analyze large volumes of data quickly.  
  **Configuration:**
    - Operating in single-node mode.
    - Heap configured via `ES_JAVA_OPTS`.
    - Data persisted in the `elasticsearch_data` volume.


- **Kibana (kibana)**  
  **Description:** Visualization interface that connects to Elasticsearch, enabling the analysis and creation of dashboards from indexed data.  
  **Configuration:**
    - Depends on Elasticsearch and connects to it to retrieve data.


- **Logstash (logstash)**  
  **Description:** Data and log processing pipeline that integrates various sources such as PostgreSQL, Kafka, and Redis for log collection, transformation, and forwarding for analysis.  
  **Configuration:**
    - Configured through the `logstash.conf` file mapped via a volume.
    - Listens on multiple ports for different types of data input.


- **Prometheus (prometheus)**  
  **Description:** Monitoring and alerting system that collects and stores application metrics, enabling performance analysis and alert configuration.  
  **Configuration:**
    - Metrics configuration defined in the `prometheus.yml` file mapped via a volume.


- **Redis (redis)**  
  **Description:** In-memory data store used as both a cache and a message broker, helping to improve the overall performance of the application.  
  **Configuration:**
    - Persistence configured with commands to save data periodically.
    - Data stored in the `redis_data` volume.


- **Wallet Service (wallet-service)**  
  **Description:** The main service of the application, developed with Spring Boot and WebFlux, that implements the business logic and integrates with other services (PostgreSQL, Kafka, Redis, Prometheus).  
  **Configuration:**
    - Built from the `./wallet-service` directory using the specified Dockerfile.
    - Environment variables defined for connecting to other services.

### Step-by-Step Instructions to Run the Commands

1. **Open the Terminal**  
   Navigate to the project's root directory, where the `docker-compose.yml` file is located.

2. **Build and Start the Containers**  
   Execute the command below to build the images (if necessary) and start all the containers:
   ```bash
   docker-compose up --build

3. To view the real-time logs of the wallet-service container, use the following command:
   ```bash
   docker-compose logs -f wallet-service

## Running the Application Using Only Docker

### Run all the required services ###

If you want to run the services without using Docker Compose, you can start each container individually with the `docker run` commands. Below is an example of how to do this:

### 1. Create the Network and Volumes ###

First, create the custom network and the required volumes:
 
        # Create the custom network
        docker network create wallet-net --driver bridge

### 2. Create the required volumes

    ```bash
    docker volume create postgres_data
    docker volume create elasticsearch_data
    docker volume create logstash_pipeline
    docker volume create redis_data

### 3. Run the Container
- Postgres
    ```bash
    docker run -d \
    --name wallet-postgres \
    --network wallet-net \
    -p 5432:5432 \
    -e PGDATA=/var/lib/postgresql/data/pgdata \
    -e POSTGRES_USER=wallet_user \
    -e POSTGRES_PASSWORD=wallet_pass \
    -e POSTGRES_DB=wallet \
    -v postgres_data:/var/lib/postgresql/data/pgdata \
    postgres:14

### 4. Run the Container
- Zookeeper
    ```bash
    docker run -d \
    --name wallet-zookeeper \
    --network wallet-net \
    -p 2181:2181 \
    -e ALLOW_ANONYMOUS_LOGIN=yes \
    bitnami/zookeeper:latest


### 5. Run the Container
- Kafka
    ```bash
    docker run -d \
    --name wallet-kafka \
    --network wallet-net \
    -p 9092:9092 \
    -e KAFKA_BROKER_ID=1 \
    -e KAFKA_ZOOKEEPER_CONNECT=wallet-zookeeper:2181 \
    -e KAFKA_ADVERTISED_LISTENERS="PLAINTEXT://wallet-kafka:9092,PLAINTEXT_HOST://localhost:9092" \
    -e KAFKA_LISTENERS="PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:9093" \
    -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP="PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT" \
    -e ALLOW_PLAINTEXT_LISTENER=yes \
    bitnami/kafka:latest


### 6. Run the Container
- Elasticsearch
    ```bash
    docker run -d \
    --name wallet-elasticsearch \
    --network wallet-net \
    -p 9200:9200 \
    -e discovery.type=single-node \
    -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
    -e ELASTICSEARCH_USERNAME=elastic \
    -e ELASTICSEARCH_PASSWORD=changeme \
    -e xpack.security.enabled=false \
    -v elasticsearch_data:/usr/share/elasticsearch/data \
    docker.elastic.co/elasticsearch/elasticsearch:7.17.7

### 7. Run the Container
- Kibana
    ```bash
    docker run -d \
    --name wallet-kibana \
    --network wallet-net \
    -p 5601:5601 \
    -e ELASTICSEARCH_HOSTS=http://wallet-elasticsearch:9200 \
    docker.elastic.co/kibana/kibana:7.17.7

### 8. Run the Container
- Logstash
    ```bash
    docker run -d \
    --name wallet-logstash \
    --network wallet-net \
    -p 5044:5044 -p 5000:5000 -p 9600:9600 \
    -v $(pwd)/logstash/logstash.conf:/usr/share/logstash/pipeline/logstash.conf \
    -e XPACK_MONITORING_ENABLED=false \
    docker.elastic.co/logstash/logstash:7.17.7

### 9. Run the Container
- Prometheus
    ```bash
    docker run -d \
    --name wallet-prometheus \
    --network wallet-net \
    -p 9090:9090 \
    -v $(pwd)/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml \
    prom/prometheus:latest

### 10. Run the Container
- Redis
    ```bash
    docker run -d \
    --name wallet-redis \
    --network wallet-net \
    -p 6379:6379 \
    -v redis_data:/data \
    redis:7 \
    redis-server --save 60 1 --loglevel warning

### 6. Run the Container
- Wallet Service

For the application service, first build the image from the Dockerfile located in `./wallet-service`:
- Run    
    ```bash
    docker build -t wallet-service-image ./wallet-service

Next, start the container:
- Run
    ```bash
    docker run -d \
    --name wallet-service \
    --network wallet-net \
    -p 8073:8073 \
    -e PORT=8073 \
    -e DATABASE_URL=wallet-postgres:5432 \
    -e DATABASE_NAME=wallet \
    -e DATABASE_USER=wallet_user \
    -e DATABASE_PASSWORD=wallet_pass \
    -e REDIS_URL=wallet-redis \
    -e REDIS_PORT=6379 \
    -e KAFKA_URL_AND_PORT=wallet-kafka:9092 \
    -e PROMETHEUS_URL=http://wallet-prometheus:9090 \
    -e LEVEL_LOGGING=INFO \
    -e KAFKA_LEVEL_LOGGING=WARN \
    -e MICROMETER_LEVEL_LOGGING=WARN \
    -e LOGSTASH_HOST=logstash \
    -e LOGSTASH_PORT=5044 \ 
    wallet-service-image

# Important Environment Variables

- **PORT**: Defines the port on which the service will be exposed.
- **DATABASE_URL**: Address of the PostgreSQL server.
- **DATABASE_NAME**: Name of the database.
- **DATABASE_USER**: User for accessing the database.
- **DATABASE_PASSWORD**: Password for accessing the database.
- **REDIS_URL**: Address of the Redis server.
- **REDIS_PORT**: Port of the Redis server.
- **KAFKA_URL_AND_PORT**: Address and port of the Kafka broker.  
  *Example in the command:* `KAFKA_URL_AND_PORT=wallet-kafka:9092`
- **PROMETHEUS_URL**: URL to access Prometheus, used for monitoring the application.
- **LOGSTASH_HOST**: Address of the Logstash server.
- **LOGSTASH_PORT**: Port of the Logstash server. 
- Other variables can be defined as needed (e.g., `LEVEL_LOGGING`, `KAFKA_LEVEL_LOGGING`, `MICROMETER_LEVEL_LOGGING`, etc).
# Accessing the Application

After starting the container, the application will be available on the configured port (by default, 8073).

Access the application using your preferred browser or tool:
- Run
    ```bash
    http://localhost:8073

# Monitoring and Documentation

- **Actuator**:  
  Monitoring endpoints are enabled (e.g., `/actuator/health`, `/actuator/metrics`, `/actuator/info`, `/actuator/prometheus`).

- **API Documentation**:
    - OpenAPI Documentation: [http://localhost:8073/api-docs](http://localhost:8073/api-docs)
    - Swagger UI: [http://localhost:8073/swagger-ui.html](http://localhost:8073/swagger-ui.html)

# Logs

Log levels can be adjusted through environment variables defined in `application.yaml` (e.g., `LEVEL_LOGGING`, `KAFKA_LEVEL_LOGGING`).

# Conclusion

Now you are ready to compile, build the Docker image, and run the Wallet Service!


# Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.4.2/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.4.2/maven-plugin/build-image.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/3.4.2/reference/web/reactive.html)
* [OTLP for metrics](https://docs.spring.io/spring-boot/3.4.2/reference/actuator/metrics.html#actuator.metrics.export.otlp)
* [Spring Data R2DBC](https://docs.spring.io/spring-boot/3.4.2/reference/data/sql.html#data.sql.r2dbc)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.4.2/reference/actuator/index.html)
* [Influx](https://docs.spring.io/spring-boot/3.4.2/reference/actuator/metrics.html#actuator.metrics.export.influx)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)
* [Accessing data with R2DBC](https://spring.io/guides/gs/accessing-data-r2dbc/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [R2DBC Homepage](https://r2dbc.io)

## Missing R2DBC Driver

Make sure to include a [R2DBC Driver](https://r2dbc.io/drivers/) to connect to your database.
### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.


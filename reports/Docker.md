# Lab2 - Containerization

## Architecture and Microservices

The original repository is a library from Apache Commons that allows operations on images like taking informations on metadata, format, converting them or manipulate them through stream usage. So to use this library we defined an application based on three components:
- **Backend**: Spring Boot backend that imports last version of Commons Imaging.
- **Frontend**: VanillaJS frontend to simulate some CRUD operation to give some context to db and docker volumes usage.
- **DB**: PostgreSQL database as an indipendent and containeraized service.

So there are two dockerfile, one for the [backend](../Dockerfile) and the other for [frontend](../frontend/Dockerfile).

### [Dockerfile Backend]((../Dockerfile))

To optimize and reduce size of final image we are using a **multi-stage build**, so we can divide what we need to build ad what we need at runtime.
- **First Stage**: Using Maven and JDK 21 to compile Commons Imaging and building the JAR of backend.
- **Second Stage**: This stage just contains JRE 21, the JAR backend and all components needed for execution.

We are also optimizing on dependencies so we can reuse Docker layers when only coded is updated and not the rest of image.

In the packaging we are skipping test and JaCoCo because they are ideally already provided and checked in the pipeline.

**No Root User**: We are defining a not root *appuser* that is the owner of `/app`, `/data/images`.
- This `/data/images` is defined so the volume can is writable without more permissions than current permissions.

### [Dockerfile Frontend](../frontend/Dockerfile)

We are defining a really simple frontend with `nginxinc`, running `nginx` on port 8080.

In the Dockerfile we are copying static files and nginx configuration.

In this way we can call the API exposed by the backend.

## Docker Compose and Service-Based Application

In the [docker-compose.yml](../docker-compose.yml) we declare db, backend and frontend services. Only the frontend service is exposed on port 8080, backend and database communicate on the Compose internal network. So we are assuring these properties:
- **Persistency**: We are keeping metadata entries in the volume `db_data` and the effective images into `image_data`.
- **Secrets**: Keeping `POSTGRES_PASSWORD` in `.env` that is not present in the repo on GitHub or in the Docker context. Providing an `.env.example` that can be used as a placeholder during the setup phase.
- **Health Checks**: PostgreSQL uses *pg_isready*, the backend calls `actuator/health` including the state of connection to DB and the frontend does an HTTP request to *nginx* on 127.0.0.1.
- **Dependencies**: Using `depends_on` with `service_healthy` means that the backend service can run only if the database is ready and the frontend only when the backend is healthy.
- **Recovery**: `restart: on-failure` restarts a service when the process ends with an error, because the health checks allow to see the state but they don't restart any service.

### Avoided Antipatterns

To avoid known antipatterns:
- There are no `latest` tags.
- `ADD` instructions and not `COPY` instructions in Dockerfiles.
- No secrets exposed in Docker configurations.
- Not using the root user.

### CD Workflow and Github Actions

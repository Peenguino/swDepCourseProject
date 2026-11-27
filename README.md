# Software Dependability Project

## List of reports

- [Lab 1 - Continuous Integration & Continuous Delivery](./reports/CICD.md)
- [Lab 2 - Containerization](./reports/Docker.md)
- [Lab 3 - Writing Test Cases with JUnit and Mockito](./reports/testing.md)
- [Lab 5 - Formal Verification with JML and OpenJML](./reports/verification.md)

## Commons Imaging Gallery - Demo

Demo composed by
- Frontend service Vanilla JS.
- Backend service Spring Boot.
- DB service PostgreSQL.

```bash
cp .env.example .env

# Define the password in .env, and then run:
docker compose up --build -d
```

Open `http://localhost:8080`.

To stop each container and the full application `docker compose down`.

The images and their entries will be handled through volume usage in `db_data` e `image_data`.
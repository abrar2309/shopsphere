# ShopSphere — Local Development, Deployment and Recovery Runbook

This document records the Windows/PowerShell workflow used to run ShopSphere, the Docker services it depends on, the build/test commands and the current cloud-demo limitation. It distinguishes local development from cloud deployment.

## 1. Current deployment status — 27 November 2026

- The source repository is available on the main branch.
- The recovered PostgreSQL container named shopsphere-pgvector was started successfully. PostgreSQL accepted connections on 127.0.0.1:5435. Its persistent Docker volume is shopsphere_pgvector_data.
- PostgreSQL accepting connections does not prove that the expected shopsphere database, schema, credentials or product data are present. Verify them with the read-only commands in Section 4 before starting Spring Boot.
- The previous remote database path used AWS Systems Manager port forwarding to local port 55433. The EC2/managed instance required by that session is stopped, so the SSM tunnel cannot be recreated while it remains stopped. The old backend container still had a JDBC URL pointing to host.docker.internal:55433/shopsphere. That is a stale tunnel target, not a working local database endpoint.
- The old shopify-backend-1 container belongs to a separate Compose project at C:\ShopSphere-Recovery\Working\ShopSphere-Review, not the current C:\Users\HP\Projects\shopsphere-github checkout. Do not use it as the source of truth for the current local run.
- No public live-demo URL or running AWS deployment is claimed. No cloud instance was started and no cloud resources were created during this recovery.
- The supplied UI screenshots are static evidence from earlier local browser sessions. They are not a live deployment, screen recording or proof that every integration is currently online.

## 2. Application components and local ports

| Component | Technology / container | Local endpoint | Role |
|---|---|---|---|
| Web UI (development) | React 19 + Vite 8 | http://localhost:5173 | Browser UI |
| Backend API | Java 21 + Spring Boot 4.1.0 | http://localhost:8080 | REST API, authentication and business logic |
| PostgreSQL | Existing shopsphere-pgvector container (PostgreSQL 18 / pgvector) | 127.0.0.1:5435 | Primary application database |
| Redis | Existing shopsphere-redis container | 127.0.0.1:6379 | Cache / coordination integration |
| Kafka | Existing shopsphere-kafka container | 127.0.0.1:9092 | Order event transport |
| BGE service (optional for normal browsing) | Python 3.11, FastAPI, Sentence Transformers | http://127.0.0.1:8001 | Product/query embeddings |
| MailHog (optional) | MailHog container | SMTP 1025; UI 8025 | Local mail capture |

The frontend URL above is the Vite development server, not the Nginx container port. If port 5173 is occupied, Vite may choose another free port such as 5174; use the URL printed in the terminal.

Keep unrelated local projects and their containers/ports untouched. Do not use docker compose down -v, docker volume prune, docker system prune or any command that removes database volumes.

## 3. Start the existing local infrastructure

Use the existing containers and volumes. These commands start containers; they do not recreate them or reset their data.

Open PowerShell:

~~~powershell
Set-Location "C:\Users\HP\Projects\shopsphere-github"

docker start shopsphere-pgvector
docker start shopsphere-redis
docker start shopsphere-kafka
docker start shopsphere-mailhog
~~~

The BGE container is optional and had previously exited with status 255. Try it only when testing semantic search:

~~~powershell
docker start shopsphere-bge-embedding
docker logs --tail 80 shopsphere-bge-embedding
Invoke-RestMethod "http://127.0.0.1:8001/health"
~~~

A successful BGE /health response should report status UP, model BAAI/bge-small-en-v1.5 and 384 dimensions. Model loading can take time on the first start. If the container exits again, preserve its logs and continue testing the catalogue, cart and order flows without semantic search.

Check dependency status:

~~~powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
pg_isready -h 127.0.0.1 -p 5435 -t 5
Test-NetConnection 127.0.0.1 -Port 6379
Test-NetConnection 127.0.0.1 -Port 9092
~~~

For Redis, the following should return PONG:

~~~powershell
docker exec shopsphere-redis redis-cli ping
~~~

## 4. Verify the existing database before booting Spring Boot

The database container is mapped from container port 5432 to host port 5435. Preserve its Docker volume.

List databases:

~~~powershell
docker exec -u postgres shopsphere-pgvector psql -U postgres -d postgres -c "\l"
~~~

If the shopsphere database exists, inspect its tables and extensions:

~~~powershell
docker exec -u postgres shopsphere-pgvector psql -U postgres -d shopsphere -c "\dt"
docker exec -u postgres shopsphere-pgvector psql -U postgres -d shopsphere -c "SELECT extname, extversion FROM pg_extension ORDER BY extname;"
~~~

These commands are read-only. If the role or authentication differs from the example, use the PostgreSQL role already configured for this local database. Do not create a replacement database or overwrite the volume merely to make the example command pass. The database metadata check in the previous recovery session did not produce a valid database/table listing, so this verification remains an explicit prerequisite.

## 5. Run the backend locally (Windows PowerShell)

Run the backend as a Windows process so the connection URLs can use 127.0.0.1. In a fresh PowerShell terminal:

~~~powershell
Set-Location "C:\Users\HP\Projects\shopsphere-github"

$env:DB_URL = "jdbc:postgresql://127.0.0.1:5435/shopsphere"
$env:DB_USERNAME = Read-Host "Local PostgreSQL username"

$SecureDbPassword = Read-Host "Local PostgreSQL password" -AsSecureString
$Bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureDbPassword)
try {
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Bstr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Bstr)
}

$env:SPRING_DATA_REDIS_HOST = "127.0.0.1"
$env:SPRING_DATA_REDIS_PORT = "6379"
$env:APP_KAFKA_ENABLED = "true"
$env:APP_KAFKA_BOOTSTRAP_SERVERS = "127.0.0.1:9092"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "127.0.0.1:9092"
$env:SHOPSPHERE_AI_BGE_BASE_URL = "http://127.0.0.1:8001"

.\mvnw.cmd spring-boot:run
~~~

The database username and password must be valid for the local database on port 5435. Do not assume RDS credentials also work on the local database. Keep this PowerShell session open while the backend is running; the environment variables are scoped to the current session/process.

Expected backend checks:

~~~powershell
Invoke-RestMethod "http://localhost:8080/actuator/health"
Test-NetConnection 127.0.0.1 -Port 8080
~~~

The BGE client defaults to http://localhost:8001 through the shopsphere.ai.bge.base-url property. The environment variable above overrides that property for a local host-process run.

## 6. Run the frontend locally

Open a second PowerShell terminal:

~~~powershell
Set-Location "C:\Users\HP\Projects\shopsphere-github\shopsphere-ui"
~~~

The frontend API client reads VITE_API_BASE_URL. For the local development workflow, set it to the backend port:

~~~powershell
if (-not (Test-Path ".env.local")) {
    "VITE_API_BASE_URL=http://localhost:8080" | Set-Content -Encoding utf8 ".env.local"
}
Get-Content ".env.local"
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1
~~~

Open the exact local URL printed by Vite, normally http://localhost:5173.

Use npm.cmd in PowerShell where the local execution policy blocks the npm PowerShell shim. The repository lockfile is checked in; npm ci installs the locked frontend dependencies.

## 7. Build and test commands

From the repository root:

~~~powershell
Set-Location "C:\Users\HP\Projects\shopsphere-github"
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

Frontend production build:

~~~powershell
Set-Location "C:\Users\HP\Projects\shopsphere-github\shopsphere-ui"
npm.cmd ci
npm.cmd run build
~~~

The recorded local verification on the recovered checkout (26 November 2026) completed 98 backend tests with zero failures and zero errors. The Vite production build also completed successfully. This is a local verification snapshot; it does not prove that the latest GitHub Actions run is green. The checked-in GitHub Actions workflow currently runs the Java 21 Maven verification job.

## 8. Docker image and Compose notes

The repository includes:
- Root Dockerfile: multi-stage Java 21 build/runtime, non-root application user, backend container port 8080 and readiness health check.
- shopsphere-ui/Dockerfile: Node 22 build stage and Nginx runtime, with VITE_API_BASE_URL supplied as a build argument.
- bge-embedding-service/Dockerfile: Python 3.11 FastAPI service, CPU PyTorch and BGE model.
- compose.yaml: backend image on host port 8081 and frontend image on host port 8082.

Important: the checked-in compose.yaml is not a complete all-in-one local stack. It does not declare PostgreSQL, Redis, Kafka or BGE. It currently points the containerized backend at host.docker.internal:5433/shopsphere, whereas the recovered pgvector container is mapped to host port 5435. It also refers to Kafka by the Docker DNS name shopsphere-kafka:9092, which requires the backend and Kafka containers to share a Docker network. Do not run docker compose up and assume it reproduces the local Vite workflow above without first reconciling those endpoint and network settings.

The image-based Compose ports are distinct from the host-development ports:
- Compose backend: host 8081 → container 8080
- Compose frontend: host 8082 → Nginx 80
- Host-run backend: 8080
- Host-run Vite UI: 5173

For the repeatable workflow used for the screenshots, run the backend and Vite UI as host processes and use the existing dependency containers.

## 9. Product walkthrough test sequence

After the health checks pass, a useful manual walkthrough is:

1. Open the home page and browse the catalogue.
2. Open Discover, try category and price controls, and open a product detail.
3. Add an in-stock product to the bag and inspect the cart.
4. Proceed to checkout and select COD or the manual UPI option. Do not treat an entered reference as a real payment confirmation; manual payment is recorded pending verification.
5. Sign in with a seller account and inspect the seller dashboard, product inventory and variants.
6. Test the BGE assistant only after the BGE /health check succeeds and the product embedding index has been populated. A screenshot in the gallery shows a query returning zero products at capture time, so it is UI evidence, not evidence of search relevance quality.
7. Where an administrator account is available, verify the manual payment through the admin workflow.

Use test accounts and test payment references only. Do not include live UPI details, customer personal data, JWTs or credentials in screenshots, issues or repository files.

## 10. AWS tunnel, deployment and demo limitation

The previous remote database path was:

~~~text
Windows / Docker backend
        |
        | host.docker.internal:55433
        v
Local SSM port-forward session
        |
        v
Running SSM-managed EC2 instance
        |
        v
Private RDS PostgreSQL
~~~

The required EC2/managed instance is currently stopped. Without a running managed instance and a valid SSM session, port 55433 is not forwarded to RDS. Starting the old backend with that URL will therefore fail to obtain PostgreSQL metadata and Hibernate cannot initialize its datasource.

This recovery intentionally did not start cloud instances or incur AWS costs. To restore a cloud demo later, an authorized operator must first review instance state, IAM/SSM permissions, network routing, RDS security-group rules and estimated costs; then establish the port-forward and verify the database before deploying the backend. No instance start, tunnel, RDS connection or public endpoint is claimed here.

The existing deploy.sh is only an artifact-upload starter script and still contains the placeholder YOUR_BUCKET_NAME; it is not a complete infrastructure deployment. Do not use it as a production deployment procedure.

Until cloud access is restored, present the GitHub repository, architecture documentation, local build/test evidence and screenshot gallery. Do not advertise a public live-demo URL or claim that a live demo video was recorded.

## 11. Recovery principles

- Keep shopsphere_pgvector_data and all other project volumes intact.
- Never run destructive Docker prune or Compose volume-removal commands during recovery.
- Never point a local backend at port 55433 unless an SSM port-forward is actively running.
- Use host loopback 127.0.0.1:5435 when the backend runs directly on Windows and the recovered pgvector container is exposed on 5435.
- Verify the database name, schema, user and password before booting Spring Boot.
- Keep secrets in the local shell or a private secret manager; never commit them.

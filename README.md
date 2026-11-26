# ShopSphere — Cloud-Native E-Commerce Platform

A full-stack e-commerce platform built with Java Spring Boot, React, PostgreSQL, Docker, and AWS infrastructure.

ShopSphere brings together customer shopping experiences, seller product management, backend APIs, persistent data storage, and AI-powered product discovery.

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot |
| Frontend | React, Vite |
| Database | PostgreSQL |
| Persistence | Spring Data JPA, Hibernate |
| Infrastructure | Docker, Docker Compose |
| Cloud | AWS |
| Load balancing | AWS Application Load Balancer |
| Object storage | Amazon S3 |
| AI engineering | RAG, embeddings, vector search |

## Core Platform Capabilities

### Customer Experience
- Product browsing and discovery
- Product search and filtering
- Shopping cart and checkout
- Orders and customer account functionality

### Seller Experience
- Product management
- Inventory management
- Product variants
- Seller-facing APIs and dashboard

### AI Engineering
- AI-powered product discovery
- Retrieval-Augmented Generation workflows
- Embedding-based product retrieval
- Context-aware product recommendations

## AWS Infrastructure

ShopSphere's cloud architecture includes:

- Custom Amazon VPC with six subnets across two Availability Zones.
- Public, private application, and database network tiers.
- Internet Gateway and Regional NAT Gateway.
- Application Load Balancer with path-based routing.
- Separate frontend and backend target groups.
- Security-group-controlled application and database communication.
- Private PostgreSQL on Amazon RDS with encrypted storage.
- Amazon S3 deployment artifact storage with encryption and public-access controls.
- EC2, EBS, AMI and snapshot recovery resources.

## Architecture

See the detailed AWS documentation:

- [AWS Architecture](docs/aws/architecture.md)
- [Network and Routing](docs/aws/network-and-routing.md)
- [Security Architecture](docs/aws/security-architecture.md)
- [Data and Storage](docs/aws/data-and-storage.md)
- [Deployment and Recovery](docs/aws/deployment-and-recovery.md)
- [Architecture Diagram](docs/aws/architecture.mmd)

## Project Documentation

- `docs/ShopSphere_System_Architecture_and_Codebase_Guide_EXPANDED.docx`
- `docs/ShopSphere_Troubleshooting_Error_Resolution_Playbook_EXPANDED.docx`
- `docs/aws/` — AWS infrastructure documentation

## Repository Structure

```text
ShopSphere/
├── src/
│   ├── main/
│   └── test/
├── shopsphere-ui/
├── docs/
│   ├── aws/
│   ├── ShopSphere_System_Architecture_and_Codebase_Guide_EXPANDED.docx
│   └── ShopSphere_Troubleshooting_Error_Resolution_Playbook_EXPANDED.docx
├── Dockerfile
├── compose.yaml
├── pom.xml
└── README.md
```

## Engineering Focus

ShopSphere demonstrates full-stack application engineering, REST API development, database design, distributed infrastructure, cloud networking, security boundaries, AI integration, and deployment architecture.

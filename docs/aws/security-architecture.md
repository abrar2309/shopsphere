# ShopSphere — Security Architecture

## Network Security

ShopSphere uses security groups to define communication boundaries between the load balancer, application services, database resources, and VPC endpoints.

## Security Group Communication

| Component | Inbound access |
|---|---|
| Load balancer | HTTP port 80 from the internet |
| Application | Ports 80 and 8080 from the load balancer security group |
| PostgreSQL database | Port 5432 from the application security group |
| VPC endpoints | HTTPS port 443 from the VPC network |

## Database Security

- Amazon RDS PostgreSQL.
- Private database accessibility.
- Encrypted database storage.
- Database access controlled through the application security group.
- Dedicated database subnet tier.

## Object Storage Security

- Amazon S3 Block Public Access controls enabled.
- AES-256 server-side encryption.
- S3 Bucket Key enabled.
- SSE-C blocked.

## Security Engineering Principles

- Least-privilege network access.
- Tier-based network segmentation.
- Private database access.
- Encryption at rest.
- Restricted service-to-service communication.

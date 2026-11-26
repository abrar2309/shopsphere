# ShopSphere — AWS Architecture

## Overview

ShopSphere is designed around a layered cloud architecture that separates internet-facing traffic, application services, and persistent data storage.

The infrastructure uses Amazon VPC, EC2, Application Load Balancer, Amazon RDS for PostgreSQL, and Amazon S3.

## Architecture Layers

### 1. Network Layer
- Dedicated VPC.
- Public, private application, and database subnet tiers.
- Six subnets distributed across two Availability Zones.
- Internet Gateway for public ingress and routing.
- Regional NAT Gateway for private application outbound connectivity.

### 2. Application Delivery Layer
- Internet-facing Application Load Balancer.
- Separate frontend and backend target groups.
- Path-based listener rules for API traffic.
- Frontend traffic handled by the frontend target group.

### 3. Application Layer
- EC2-based application infrastructure.
- Spring Boot backend service.
- Frontend web application.
- Application traffic organized through load-balancer target groups.

### 4. Data Layer
- Amazon RDS PostgreSQL.
- Dedicated database subnet group.
- Encrypted database storage.
- Security-group-controlled database access.

### 5. Artifact Storage Layer
- Amazon S3 deployment artifact storage.
- Server-side encryption.
- S3 Block Public Access controls.

## Engineering Principles

- Network segmentation.
- Separation of application and database tiers.
- Controlled service-to-service communication.
- Load-balancer-based traffic distribution.
- Encrypted persistent storage.
- Infrastructure recovery through image and snapshot resources.

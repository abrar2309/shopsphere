# ShopSphere — Network and Traffic Routing

## VPC Design

The ShopSphere network is organized into three subnet tiers:

| Tier | Purpose |
|---|---|
| Public | Internet-facing load-balancing infrastructure |
| Private application | Application workloads and outbound connectivity |
| Database | Private database resources |

The six-subnet design spans two Availability Zones.

## Routing Architecture

### Public Subnets
Public subnet route tables use an Internet Gateway for internet routing.

### Private Application Subnets
Private application subnet route tables use a Regional NAT Gateway for outbound connectivity.

The Regional NAT configuration includes automatic IP scaling and Availability Zone provisioning.

### Database Subnets
Database subnet route tables retain local VPC routing, keeping database resources in a separate network tier.

## Application Load Balancer

An internet-facing Application Load Balancer distributes incoming traffic using listener rules.

| Path pattern | Destination |
|---|---|
| `/products` and `/products/*` | Backend |
| `/auth*` | Backend |
| `/cart*` | Backend |
| `/categories*` | Backend |
| `/orders` and `/orders/*` | Backend |
| Default route | Frontend |

### Target Groups

- Backend target group: port 8080.
- Frontend target group: port 80.

This design separates frontend delivery from backend API routing.

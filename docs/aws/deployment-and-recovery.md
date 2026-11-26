# ShopSphere — Deployment and Recovery Engineering

## Application Infrastructure

The ShopSphere AWS environment includes EC2 application infrastructure, an Application Load Balancer, separate frontend and backend target groups, and private PostgreSQL database resources.

## Traffic Management

The Application Load Balancer uses listener rules to route API paths to the Spring Boot backend target group and default traffic to the frontend target group.

## EC2 and EBS Investigation

Infrastructure inspection covered application instances, storage mappings, attached EBS volumes, and recovery resources.

The recovery investigation included:
- EC2 instance metadata.
- EBS volume inventory.
- EBS snapshot inventory.
- Instance block-device mappings.
- Custom AMI metadata.

## Image-Based Recovery

A ShopSphere recovery AMI and its associated backing snapshot were included in the collected recovery evidence.

Image-based recovery resources form part of the infrastructure recovery design.

## Database Recovery

The RDS recovery evidence includes automated and manual PostgreSQL snapshots with encrypted storage.

## Artifact Recovery

Application release artifacts are organized in Amazon S3, providing a separate storage location for packaged application releases.

## Operational Engineering

The infrastructure work includes examination of:
- EC2 instance states.
- Load-balancer target groups.
- Listener rules and routing.
- EBS storage resources.
- AMI and snapshot metadata.
- RDS configuration and database snapshots.
- S3 release artifact inventory.

## Engineering Summary

ShopSphere brings together network architecture, application traffic management, private database infrastructure, encrypted object storage, and image- and snapshot-based recovery resources.

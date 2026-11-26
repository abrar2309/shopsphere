# ShopSphere — Data and Storage Architecture

## Amazon RDS PostgreSQL

The ShopSphere data tier uses Amazon RDS PostgreSQL.

| Configuration | Value |
|---|---|
| Database engine | PostgreSQL 18.3 |
| Instance class | db.t4g.micro |
| Allocated storage | 20 GB |
| Storage encryption | Enabled |
| Public accessibility | Disabled |
| Database subnet group | Dedicated database subnets |
| Backup retention | 1 day |

Database subnet placement spans two Availability Zones.

## Database Recovery Resources

The collected infrastructure evidence includes automated and manual database snapshots.

Snapshot encryption and availability were recorded during the infrastructure inspection.

## Amazon S3

Amazon S3 provides deployment artifact storage.

Storage security configuration includes:

- AES-256 server-side encryption.
- S3 Bucket Key.
- All four Block Public Access settings enabled.
- SSE-C blocked.

## Release Artifact Organization

The collected release inventory records application artifacts under a versioned release prefix.

This provides an organized storage structure for application release packages.

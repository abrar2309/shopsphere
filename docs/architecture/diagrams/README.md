# Editable Architecture Diagram Sources

These diagrams are Mermaid source files. GitHub renders Mermaid blocks embedded in Markdown directly. The `.mmd` files are retained as standalone editable source and can be opened in Mermaid-compatible tools.

| Diagram | Purpose |
|---|---|
| [system-context.mmd](system-context.mmd) | Actors, client, backend and dependencies |
| [application-hld.mmd](application-hld.mmd) | Application containers and infrastructure |
| [component-map.mmd](component-map.mmd) | Spring Boot layers and service boundaries |
| [checkout-sequence.mmd](checkout-sequence.mmd) | Order creation, stock locking and outbox transaction |
| [payment-sequence.mmd](payment-sequence.mmd) | Separate manual/simulated payment and admin verification flow |
| [bge-indexing-sequence.mmd](bge-indexing-sequence.mmd) | Product document indexing and vector upsert |
| [bge-search-sequence.mmd](bge-search-sequence.mmd) | Query embedding and semantic retrieval |
| [kafka-outbox-flow.mmd](kafka-outbox-flow.mmd) | Outbox publishing and independent consumer groups |
| [core-data-model.mmd](core-data-model.mmd) | Core JPA entity relationships |
| [security-request-flow.mmd](security-request-flow.mmd) | JWT and authorization path |
| [deployment-topology.mmd](deployment-topology.mmd) | Local Compose and AWS reference boundary |

The Mermaid sources are maintained as text, not generated raster illustrations.

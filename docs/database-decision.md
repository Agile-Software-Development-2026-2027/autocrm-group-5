# Database Decision -Transcript Strorage
## 1. Context
    Transcripts are currently kept in server RAM (InMemoryJobRepository) and are lost on every restart. We need one persistent store for transcripts from both ingestion
### channels:
    1. Manual upload -> WhisperX (MANUAL): Insert as PROCESSING, later update to DONE (with text) or FAILED
    2. Fireflies webhook (FIREFLIES): Insert directly as DONE; webhooks may be retried, so duplicates must be prevented
### Data: 
    id, title, source, status, text, createdAt are the fields to be recorded.
### Queries: 
    retrieving single transcript, list newest(all) transcriptions.
## 2. Database Options considered:
    H2 (embedded, file mode),  PostgreSQL, MongoDB, Firebase (Cloud Firestore).
## Comparison
| Criterion | H2 (file) | PostgreSQL | MongoDB | Firestore |
|---|---|---|---|---|
| **1. Data model & flexibility** | Relational tables. A new field = a new entity field; Hibernate adds the column on startup | Relational tables, plus `jsonb` columns for free-form extras | Schema-free documents; any document can carry extra fields | Schema-free documents |
| **2. Durability & production readiness** | Data in a local file, survives restarts. No guarantee on power failure. One server instance only; not meant for production | Production-grade; crash-safe; supports many app instances | Production-grade; replica sets for availability | Fully managed by Google |
| **3. Query & data capability** | SQL; sorting, filtering, unique constraints. Large text as CLOB | SQL plus built-in full-text search with relevance ranking. `TEXT` for any transcript size | Rich queries and text indexes. Max 16 MB per document | Limited query model. Max 1 MiB per document |
| **4. Fit with existing code & stack** | Needs an entity class (records can't be JPA entities) | Same JPA code as H2 | Spring Data MongoDB behind repository; can map the record directly.| No Spring Data module; manual SDK code. Vendor lock-in |
| **5. Setup & configuration (both sources)** | A few lines in `application.properties`; runs inside the app; nothing to install | A running server per developer (Docker or local install), URL, user, password | A running server per developer (Docker or local install) or an Atlas account | Google Cloud project, service-account key, network access |
| **6. Testing & CI** | Same engine in memory for tests (`@DataJpaTest`); **no CI change** | Tests via Testcontainers; Docker needed locally and in CI | Testcontainers or a third-party embedded binary| Emulator or real cloud project in tests |
| **7. Cost & licensing** | Free | Free | Free. Atlas free tier: 512 MB | Free quota (1 GiB, 20,000 writes/day), then billing |

## Conclusion:
If the transcript fields stay as defined in the Data section, with occasional planned additions, I recommend **PostgreSQL**: it enforces that structure at the database level, and new columns are added safely through migrations. If the fields are expected to change frequently, or to differ from one transcript to another (for example raw Fireflies data or AI-extracted fields), I recommend **MongoDB**, because its schema-free documents absorb those changes without migrations.
if we are not into configuring Docker and staff the H2 is enough to overcome the data loss on every restart too. 
**Decision**: **H2 (embedded file mode)** with Spring Data JPA and Flyway.


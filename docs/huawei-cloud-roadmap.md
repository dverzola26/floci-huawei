# Huawei Cloud roadmap

This roadmap turns the AWS-focused Floci baseline into an independent local Huawei Cloud emulator
without attempting a full multi-cloud rewrite up front.

## Product goal

Developers should be able to run supported Huawei Cloud workloads locally and in CI by pointing
official Huawei Cloud SDKs and infrastructure tools at Floci Huawei Cloud, using non-production
credentials and predictable local resources.

## Delivery phases

| Phase | Scope | Exit criteria |
|---|---|---|
| 0 — Foundation | Project direction, architecture, service priorities, compatibility policy | Decisions documented; no runtime behavior changed |
| 1 — Core protocol | Huawei routing, SDK-HMAC-SHA256 verification, regions, projects, domains, request IDs, common errors | A signed official SDK request reaches a test service and receives a Huawei-shaped response |
| 2 — OBS | Buckets, objects, listing, metadata, multipart upload, signed URLs | Huawei OBS SDK integration tests pass for the documented operations |
| 3 — FunctionGraph | Functions, versions, aliases, invocation, logs, Docker execution | Official SDK can create and invoke a local function |
| 4 — ECS and VPC | Servers, images, flavors, VPCs, subnets, security groups, NICs | Official SDK can provision and inspect a locally represented server and network |
| 5 — RDS | PostgreSQL and MySQL instances, lifecycle, endpoints, credentials | Official SDK can create an instance and connect to its local database container |
| 6 — Platform services | SWR, CCE, SMN, KMS/DEW, CSMS, Cloud Eye/LTS | Each service satisfies its published operation-level compatibility matrix |
| 7 — Tooling and release | Terraform compatibility, CLI examples, Testcontainers module, native image, Docker publishing | Repeatable CI build and versioned preview release |

## MVP scope

The first preview release includes:

- Common Huawei request routing and authentication context
- Region and project isolation
- OBS core object operations
- FunctionGraph create, update, invoke, versions, and aliases
- Basic ECS and VPC control-plane operations
- RDS PostgreSQL and MySQL container-backed instances
- Java and Python SDK compatibility tests
- Docker Compose startup and persistent storage

## Reuse map

Reuse means sharing internal behavior, not exposing AWS APIs as Huawei APIs.

| Huawei Cloud service | Candidate Floci engine | Required Huawei-specific surface |
|---|---|---|
| OBS | S3 object storage | OBS signing, endpoints, headers, XML errors, resource behavior |
| FunctionGraph | Lambda container lifecycle | FunctionGraph paths, models, IAM context, invocation responses |
| ECS | EC2 container lifecycle | Huawei ECS models, project scoping, flavors, server actions |
| VPC | EC2 network model | VPC/subnet/security-group APIs and Huawei resource IDs |
| RDS | RDS database containers | Huawei RDS API, jobs, instance states, endpoints |
| SWR | ECR registry | Huawei repository API and authentication flow |
| CCE | EKS/k3s | Huawei CCE cluster and node-pool APIs |
| SMN | SNS delivery engine | Topic, subscription, template, and message APIs |
| KMS/DEW | KMS cryptographic engine | Huawei key models, APIs, errors, and identity rules |
| CSMS | Secrets Manager storage | Huawei secret/version APIs and rotation metadata |

## Initial engineering backlog

1. Record canonical Huawei API samples from official SDK-generated requests.
2. Add provider-neutral request metadata without changing existing AWS behavior.
3. Implement Huawei endpoint routing and common error envelopes.
4. Implement SDK-HMAC-SHA256 canonical request verification behind an opt-in setting.
5. Add region, project, and domain-aware storage namespaces.
6. Build a minimal diagnostic service used only by compatibility tests.
7. Implement OBS as the first public Huawei service.

## Non-goals for the preview

- Complete parity with every Huawei Cloud service or operation
- Connecting to or modifying real Huawei Cloud resources
- Billing, marketplace, support-plan, or enterprise-account emulation
- Huawei Cloud Stack or HCS Online compatibility
- Replacing the official Huawei SDKs with project-specific clients
- Removing the inherited AWS implementation before Huawei services reach parity

## Release gates

Every preview release must:

- Build on Java 25 using the Maven wrapper
- Pass existing unaffected Floci tests
- Pass Huawei SDK compatibility tests for supported operations
- Document service limitations and intentional deviations
- Start without real cloud credentials or network access
- Retain third-party license and attribution notices


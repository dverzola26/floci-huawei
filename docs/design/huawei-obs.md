# Huawei OBS compatibility contract

**Status:** Proposed  
**Target phase:** Phase 2  
**Phase 1 baseline:** `b91a09eb`

## Purpose

Phase 2 adds Object Storage Service (OBS) as the first public Huawei Cloud service in Floci Huawei
Cloud. An application using a supported official OBS SDK operation must be able to point the SDK
at the local endpoint without translating the request into an AWS API.

This contract defines the first supported operation set, OBS-specific authentication and routing,
state semantics, errors, persistence expectations, and the pull request sequence. It does not add
runtime behavior by itself.

## Compatibility principles

1. Expose Huawei OBS REST/XML requests and responses, including `x-obs-*` headers.
2. Keep the OBS-facing controller and models separate from the inherited S3 wire surface.
3. Reuse provider-neutral object-storage behavior where doing so does not leak AWS contracts.
4. Validate supported operations with official Huawei OBS Java and Python SDK clients.
5. Keep existing AWS S3 behavior unchanged.
6. Mark an operation supported only after its success, validation, not-found, conflict, and
   pagination or lifecycle behavior is covered where applicable.
7. Document intentional deviations rather than silently returning approximate responses.

## Local endpoint contract

The initial local endpoint is the existing Floci listener, normally:

```text
http://localhost:4566
```

Phase 2 initially requires **path-style addressing**:

```text
GET    /                              list buckets
PUT    /{bucket}                      create bucket
HEAD   /{bucket}                      inspect bucket
DELETE /{bucket}                      delete bucket
PUT    /{bucket}/{object-key}         put object
GET    /{bucket}/{object-key}         get object
HEAD   /{bucket}/{object-key}         inspect object
DELETE /{bucket}/{object-key}         delete object
```

Object keys preserve embedded slashes. Percent-encoded path segments are decoded exactly once for
resource lookup. Query parameters select listing, metadata, multipart, and signed-URL behavior.

Virtual-hosted bucket routing such as `bucket.obs.localhost` is deferred until path-style behavior
is stable. Official SDK compatibility tests must enable their path-style option when the SDK
provides one.

## OBS request classification

OBS authentication is distinct from the general Huawei Cloud SDK algorithms implemented in Phase
1. The first OBS release recognizes:

- Header authentication: `Authorization: OBS <access-key>:<signature>`
- Query authentication for signed URLs: `AccessKeyId`, `Expires`, and `Signature`

OBS classification must occur only for routes that match the OBS endpoint contract. A generic
query string containing `Signature` must never reclassify an inherited AWS request.

The following are deferred:

- `OBS4-HMAC-SHA256`
- Temporary security-token authentication
- Browser POST policies
- Anonymous public-bucket access and ACL evaluation

When local signature validation is disabled, the emulator still parses authentication safely and
uses non-production placeholder credentials. When validation is enabled, it verifies the OBS
canonical string with the configured local AK/SK pair. Real cloud credentials are neither required
nor recommended.

## REST/XML behavior

OBS uses a REST-style API with XML control-plane documents and binary object bodies.

Every response must include an OBS request identifier. The implementation will return
`x-obs-request-id`; any additional request tracing headers required by the pinned SDK versions
will be recorded by compatibility fixtures before implementation.

Service errors use the OBS XML envelope:

```xml
<Error>
  <Code>NoSuchBucket</Code>
  <Message>The specified bucket does not exist.</Message>
  <RequestId>...</RequestId>
  <HostId>...</HostId>
</Error>
```

Object downloads stream the stored bytes and preserve standard content headers. Control-plane
responses use UTF-8 XML. Successful empty responses do not include invented JSON bodies.

## Resource scope and isolation

OBS buckets are not modeled as project-scoped control-plane resources.

- Bucket names are unique across the local emulator, matching OBS's globally unique naming model.
- The creating access key is recorded as the local owner.
- A bucket's region is fixed when it is created and defaults to
  `floci.huawei.default-region` when the request does not supply a location.
- Objects and multipart uploads inherit the bucket's region and owner.
- Storage keys include a provider namespace so an OBS bucket cannot collide with an inherited S3
  bucket that has the same name.
- State must honor the configured `memory`, `persistent`, `hybrid`, and `wal` storage modes.

Domain and project IDs remain available in `HuaweiRequestContext` for tracing, but they do not
change bucket-name uniqueness in the Phase 2 contract.

## Object and metadata semantics

The first release supports:

- Arbitrary binary object bodies, including empty objects
- UTF-8 object keys and keys containing slashes
- `Content-Type`, `Content-Length`, `Content-Disposition`, `Content-Encoding`,
  `Content-Language`, and `Cache-Control`
- User metadata using `x-obs-meta-*`
- Stable quoted ETags
- `Last-Modified`
- Byte-range GET requests with `206 Partial Content`
- Conditional GET/HEAD using ETag and modification-time headers required by the SDK tests
- Copy-on-write-safe reads so callers cannot mutate stored data accidentally

Single-part ETags will be the hexadecimal MD5 of the stored payload unless compatibility research
for the pinned SDK versions proves a different observable requirement. Multipart ETags are stable
and distinguishable but are not promised to match real OBS internal encryption or storage details.

## Listing semantics

Bucket listing supports `prefix`, `marker`, `delimiter`, and `max-keys`.

- Results are ordered lexicographically by encoded object key.
- `max-keys` is bounded and validated.
- Truncated responses return the correct continuation marker.
- A delimiter groups common prefixes without returning those keys as ordinary contents.
- Empty listings return valid OBS XML collections rather than a missing body.

ListObjectsV2-style continuation tokens are deferred unless the pinned SDK version requires them
for its standard list operation.

## Multipart semantics

Multipart uploads are isolated by bucket, key, and upload ID.

- Initiation creates a stable upload ID.
- Part numbers range from 1 through 10,000.
- Re-uploading a part number replaces that part.
- Listing parts is ordered by part number and paginated.
- Completion validates the submitted part order and ETags, then atomically publishes the object.
- Aborting removes staged parts and leaves no object.
- Incomplete uploads remain private to multipart APIs and survive restart in persistent modes.
- Completion enforces OBS-compatible minimum-part rules for all non-final parts.

## Initial operation-level matrix

Status values are `Planned` for Phase 2 delivery and `Deferred` for work outside the first OBS
preview. An operation becomes `Supported` only in the pull request that adds passing official SDK
tests.

| Capability | REST shape | Initial status | Required verification |
|---|---|---|---|
| List buckets | `GET /` | Planned | Java and Python SDK; owner and region fields |
| Create bucket | `PUT /{bucket}` | Planned | default and explicit region; duplicate errors |
| Head/get bucket metadata | `HEAD /{bucket}` | Planned | existence, location, request ID |
| Delete bucket | `DELETE /{bucket}` | Planned | empty success; non-empty conflict |
| Put object | `PUT /{bucket}/{key}` | Planned | bytes, empty body, content headers, metadata |
| Get object | `GET /{bucket}/{key}` | Planned | streaming bytes, ranges, conditions |
| Head/get object metadata | `HEAD /{bucket}/{key}` | Planned | ETag, length, timestamps, metadata |
| Delete object | `DELETE /{bucket}/{key}` | Planned | idempotent behavior required by SDK |
| List objects | `GET /{bucket}` | Planned | prefix, marker, delimiter, max-keys |
| Initiate multipart upload | `POST /{bucket}/{key}?uploads` | Planned | upload ID and isolation |
| Upload part | `PUT /{bucket}/{key}?partNumber=&uploadId=` | Planned | ETag and replacement |
| List parts | `GET /{bucket}/{key}?uploadId=` | Planned | ordering and pagination |
| Complete multipart upload | `POST /{bucket}/{key}?uploadId=` | Planned | validation and atomic publish |
| Abort multipart upload | `DELETE /{bucket}/{key}?uploadId=` | Planned | cleanup and idempotency |
| Header signature | `Authorization: OBS ...` | Planned | known-good and tampered fixtures |
| Signed GET URL | query authentication | Planned | expiry, signature, encoded key |
| Signed PUT URL | query authentication | Planned | upload and expiry |
| Virtual-hosted buckets | bucket in host | Deferred | DNS and SDK endpoint behavior |
| OBS4 signing | `OBS4-HMAC-SHA256` | Deferred | official signature vectors |
| ACLs and anonymous access | `?acl` | Deferred | owner and grant evaluation |
| Bucket/object versioning | `?versioning` | Deferred | version IDs and delete markers |
| Server-side encryption | `x-obs-server-side-encryption*` | Deferred | header and key behavior |
| Lifecycle, CORS, website, logging | bucket subresources | Deferred | per-feature contracts |
| Notifications and replication | bucket subresources | Deferred | cross-service integration |
| Browser POST uploads | multipart form policy | Deferred | policy signature and conditions |

## Required error behavior

The first implementation must map at least these observable conditions:

| Condition | OBS code | HTTP status |
|---|---|---:|
| Invalid bucket name | `InvalidBucketName` | 400 |
| Malformed argument or XML | `InvalidArgument` or `MalformedXML` | 400 |
| Unknown local access key | `InvalidAccessKeyId` | 403 |
| Invalid signature | `SignatureDoesNotMatch` | 403 |
| Expired signed URL | `AccessDenied` | 403 |
| Missing bucket | `NoSuchBucket` | 404 |
| Missing object | `NoSuchKey` | 404 |
| Duplicate bucket owned locally | `BucketAlreadyOwnedByYou` | 409 |
| Duplicate bucket owned by another local identity | `BucketAlreadyExists` | 409 |
| Delete non-empty bucket | `BucketNotEmpty` | 409 |
| Missing multipart upload | `NoSuchUpload` | 404 |
| Invalid multipart part or order | `InvalidPart` or `InvalidPartOrder` | 400 |
| Multipart part below minimum size | `EntityTooSmall` | 400 |

Error bodies must contain the same request ID returned in the response headers.

## Internal architecture

The Huawei surface will use its own thin REST/XML controller, validation, models, and exception
mapping. Object bytes and metadata may reuse or extract provider-neutral storage primitives from
the S3 implementation, but the OBS controller must not call an AWS controller or serialize AWS
response models.

Proposed components:

| Component | Responsibility |
|---|---|
| `ObsRequestClassifier` | Recognize OBS header and query authentication only on OBS routes |
| `ObsAuthorization` | Parsed header or signed-query credentials |
| `ObsSignatureVerifier` | OBS canonical-string and expiry verification |
| `ObsController` | REST routing, headers, XML, ranges, and streaming |
| `ObsService` | Bucket, object, listing, and multipart behavior |
| `ObsStorage` | Provider-namespaced durable state through `StorageFactory` |
| `ObsException` / mapper | OBS XML errors and request identifiers |
| OBS XML models | Reflection-safe request and response documents |

Names may be refined during implementation, but the layer boundaries and wire contract are fixed by
this document.

## Pull request sequence

1. **Contract and matrix** — this document; no runtime behavior.
2. **Routing and OBS authentication** — request classification, path-style routing, XML errors,
   header signature fixtures, and inherited AWS isolation tests.
3. **Bucket lifecycle** — list, create, metadata/head, delete, region and owner semantics.
4. **Object lifecycle** — put, get, head, delete, streaming, ranges, and metadata.
5. **Listing and pagination** — prefix, marker, delimiter, max-keys, and common prefixes.
6. **Multipart uploads** — initiate, upload/list parts, complete, abort, and persistence.
7. **Signed URLs** — signed GET and PUT, expiry, encoding, and tamper rejection.
8. **Official SDK compatibility** — pinned Java and Python OBS SDK round trips and a documented
   supported/deferred matrix.
9. **Persistence and regression gate** — all storage modes, restart behavior, AWS S3 isolation, and
   native-image coverage.

Each behavior pull request must include focused tests and keep existing AWS and Huawei core tests
green.

## Phase 2 acceptance criteria

- Official Huawei OBS Java and Python SDKs complete all operations marked Supported.
- Path-style local endpoints require no project-specific client or request translation.
- Header authentication and signed GET/PUT URLs accept valid local credentials and reject
  tampering when validation is enabled.
- Bucket, object, metadata, listing, and multipart state survive restart in persistent modes.
- Region and owner semantics are stable and documented.
- OBS and inherited S3 resources and routing do not interfere with each other.
- Errors deserialize through the pinned official SDKs with the expected code and request ID.
- Unsupported operations return an explicit OBS error rather than a false success.
- No real Huawei Cloud credentials or network calls are used.

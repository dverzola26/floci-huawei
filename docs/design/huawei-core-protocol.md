# Huawei core protocol design

**Status:** Proposed

**Target phase:** Phase 1

## Purpose

Phase 1 establishes the shared protocol foundation required by every Huawei Cloud service. It does
not implement a public service yet. The implementation must preserve inherited AWS behavior while
making Huawei-authenticated requests independently identifiable, scoped, validated, and reported.

This design was derived from the official Huawei Cloud Java and Python SDK v3 signing and
credential implementations.

## Research baseline

| SDK | Core version | Reviewed commit |
|---|---:|---|
| Huawei Cloud SDK for Java v3 | 3.1.213 | `9aba4fd5531d` |
| Huawei Cloud SDK for Python v3 | 3.1.212 | `28849cc43a39` |

The signing implementations, credential context, exception parsing, and their published test
vectors were reviewed. These repositories are research inputs and are not vendored into Floci
Huawei Cloud.

## Supported authentication algorithms

| Algorithm | Authorization prefix | Phase 1 behavior |
|---|---|---|
| Standard AK/SK | `SDK-HMAC-SHA256` | Parse and optionally validate |
| Derived AK/SK | `V11-HMAC-SHA256` | Parse and optionally validate |
| HMAC-SM3 | `SDK-HMAC-SM3` | Recognize and return a clear unsupported-algorithm error |
| ECDSA P-256 | `SDK-ECDSA-P256-SHA256` | Deferred |
| SM2/SM3 | `SDK-SM2-SM3` | Deferred |

Supporting the derived algorithm is important for SDK configurations that deliberately enable
derived authentication against non-Huawei endpoints.

## Request classification

A request is classified as Huawei AK/SK traffic when its `Authorization` header starts with a
recognized Huawei signing algorithm. The classifier must not infer Huawei traffic from generic
JSON content types or versioned paths alone because those signals overlap inherited AWS services.

Classification runs before the AWS account-context filter but does not rewrite the path or body.
It stores the provider decision as a request property for Huawei filters, controllers, and error
mappers.

Token-based IAM requests using `X-Auth-Token` will be added with the IAM service. They are outside
the Phase 1 AK/SK classifier.

## Authorization formats

### Standard

```text
SDK-HMAC-SHA256 Access=<access-key>, SignedHeaders=<headers>, Signature=<hex>
```

### Derived

```text
V11-HMAC-SHA256 Credential=<access-key>/<date>/<region>/<service>, SignedHeaders=<headers>, Signature=<hex>
```

The parser will return an immutable value containing the algorithm, access key, signed-header
names, signature, and optional date, region, and service scope. Malformed fields fail with a
Huawei-shaped `401` response when signature validation is enabled. When validation is disabled,
the header must still be safely parseable before it is used for request context.

## Canonical request

For `SDK-HMAC-SHA256`, the canonical request is:

```text
HTTP_METHOD
CANONICAL_URI
CANONICAL_QUERY_STRING
CANONICAL_HEADERS
SIGNED_HEADERS
PAYLOAD_HASH
```

The string to sign is:

```text
SDK-HMAC-SHA256
X_SDK_DATE
SHA256_HEX(CANONICAL_REQUEST)
```

The signature is the lowercase hexadecimal result of HMAC-SHA256 using the secret key.

For `V11-HMAC-SHA256`, the string to sign additionally contains the scope
`<date>/<region>/<service>`. Its signing key is derived using the SDK-compatible HKDF procedure
before HMAC-SHA256 is calculated.

### Canonicalization requirements

- Use the uppercase HTTP method.
- Decode and re-encode each URI path segment using UTF-8 percent encoding.
- Preserve `/` as the root path and append a trailing slash to other canonical URIs.
- Sort query parameters by encoded key and value; preserve repeated parameters.
- Use the exact header names declared by `SignedHeaders`, normalized to lowercase and sorted.
- Trim signed-header values and include the terminating newline after canonical headers.
- Use SHA-256 of the body, the standard empty-body hash, or `UNSIGNED-PAYLOAD` when declared by
  `X-Sdk-Content-Sha256`.
- Exclude headers containing `_`, matching official SDK behavior.
- Treat `Host` and `X-Sdk-Date` as required signed headers.
- Compare signatures in constant time.

The implementation will be verified against fixed signature vectors published in the Java and
Python SDK test suites. SDK source code will not be copied into the project.

## Huawei request context

Huawei request state remains separate from the AWS-focused `RequestContext` during Phase 1.

```text
HuaweiRequestContext
  requestId
  accessKey
  regionId
  projectId
  domainId
  serviceName
  authenticationAlgorithm
```

### Project resolution

1. Project ID in a matched route path
2. `X-Project-Id`
3. Configured default project ID

If the path and header both contain different project IDs, the request fails instead of silently
selecting one.

### Domain resolution

1. Domain ID in a matched route path
2. `X-Domain-Id`
3. Configured default domain ID

### Region resolution

1. Region in a `V11-HMAC-SHA256` credential scope
2. Region resolved from a future Huawei service endpoint host
3. Configured default Huawei region

The standard `SDK-HMAC-SHA256` header does not contain a region. A client using a plain
`localhost` endpoint therefore uses the configured default region. Project and region are kept as
separate fields because a project identifies a regional tenant scope but does not encode a region
in a form the emulator can safely derive.

## Error contract

Protocol and authentication errors use the Huawei SDK-compatible JSON envelope:

```json
{
  "error_code": "FLOCI.HUAWEI.AUTH.0001",
  "error_msg": "The request signature is invalid."
}
```

Every Huawei response includes `X-Request-Id`. Service implementations may later use either the
`error_code`/`error_msg` or `code`/`message` shape when required by the real service; both are
understood by the official SDKs.

| Condition | Status |
|---|---:|
| Missing required signing field | 401 |
| Unknown local access key during validation | 401 |
| Invalid signature | 401 |
| Expired or future-dated request beyond allowed skew | 401 |
| Recognized but unsupported algorithm | 501 |
| Project or domain scope conflict | 400 |
| Huawei-authenticated unknown operation | 404 |

## Configuration

Proposed configuration under `floci.huawei`:

```yaml
floci:
  huawei:
    enabled: true
    default-region: region-1
    default-project-id: "00000000000000000000000000000000"
    default-domain-id: "00000000000000000000000000000000"
    auth:
      validate-signatures: false
      access-key: test
      secret-key: test
      max-clock-skew-seconds: 900
```

Environment variables follow Quarkus mapping conventions, for example
`FLOCI_HUAWEI_DEFAULT_REGION` and `FLOCI_HUAWEI_AUTH_VALIDATE_SIGNATURES`.

Signature validation is disabled by default to preserve the local-emulator experience. When it is
disabled, credentials may be non-production placeholder values. When enabled, the configured
local AK/SK pair is used; real Huawei Cloud credentials are neither needed nor recommended.

## Planned components

| Component | Responsibility |
|---|---|
| `HuaweiAuthorization` | Parsed immutable authorization value |
| `HuaweiAuthorizationParser` | Strict parsing for standard and derived headers |
| `HuaweiRequestClassifier` | Identify Huawei traffic without disturbing AWS requests |
| `HuaweiCanonicalRequest` | URI, query, header, and payload canonicalization |
| `HuaweiSignatureVerifier` | Standard HMAC and derived HKDF/HMAC verification |
| `HuaweiRequestContext` | Request-scoped Huawei identity and tenancy metadata |
| `HuaweiRequestContextFilter` | Populate context and enforce optional validation |
| `HuaweiException` | Provider-specific error code, status, and message |
| `HuaweiExceptionMapper` | JSON error envelope plus `X-Request-Id` |

These classes will live under a Huawei-specific core package. Existing AWS classes will not be
renamed or generalized during this phase.

## Pull request sequence

1. **Context and errors** — configuration, classifier, request context, request IDs, and exception
   mapping.
2. **Standard signing** — parser, canonical request, `SDK-HMAC-SHA256` verifier, and official test
   vectors.
3. **Derived signing** — `V11-HMAC-SHA256`, HKDF verification, and derived SDK vectors.
4. **SDK compatibility harness** — test-only route exercised by pinned Huawei Java SDK core and
   request fixtures generated by the Python SDK.

Each pull request must keep existing AWS tests green and must not add a production-only diagnostic
API.

## Phase 1 acceptance criteria

- Huawei and AWS requests are classified without interfering with each other.
- Standard and derived authorization headers parse correctly.
- Canonicalization reproduces official Java and Python SDK test signatures.
- Optional signature validation accepts a known-good request and rejects tampering.
- Project, domain, region, service, access key, and request ID are available in request scope.
- Huawei errors deserialize correctly through the official SDK exception handling.
- Existing Floci tests remain unchanged and pass.
- No real Huawei credentials or network calls are used.

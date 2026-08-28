#!/usr/bin/env python3
"""Regenerate the committed Huawei Python SDK signing fixtures."""

import argparse
import json
from pathlib import Path
from types import SimpleNamespace

from huaweicloudsdkcore.sdk_request import SdkRequest
from huaweicloudsdkcore.signer.signer import DerivationAKSKSigner, Signer


SDK_VERSION = "3.1.212"
SDK_COMMIT = "28849cc43a39"
HOST = "service.endpoint.myhuaweicloud.com"
SDK_DATE = "20200608T023900Z"


def request(method: str, body: str) -> SdkRequest:
    return SdkRequest(
        method=method,
        schema="https",
        host=HOST,
        resource_path="/resources",
        query_params=[("size", "1")],
        header_params={"X-Sdk-Date": SDK_DATE, "TEST_UNDERSCORE": "TEST_VALUE"},
        body=body,
    )


def fixture(name: str, signed: SdkRequest, algorithm: str, **scope: str) -> dict:
    body = signed.body.decode("utf-8") if isinstance(signed.body, bytes) else signed.body
    return {
        "name": name,
        "algorithm": algorithm,
        "method": signed.method,
        "host": signed.host,
        "sdkDate": signed.header_params["X-Sdk-Date"],
        "body": body,
        **scope,
        "authorization": signed.header_params["Authorization"],
    }


def generate() -> dict:
    credentials = SimpleNamespace(ak="AccessKey", sk="SecretKey")
    standard = Signer(credentials).sign(request("GET", ""))
    derived = DerivationAKSKSigner(credentials).sign(
        request("POST", '{"name":"test","id":1}'),
        derived_auth_service_name="demo",
        region_id="test-region-1",
    )
    return {
        "generatedBy": {
            "sdk": "Huawei Cloud SDK for Python v3 core",
            "version": SDK_VERSION,
            "commit": SDK_COMMIT,
        },
        "requests": [
            fixture("standard-get", standard, "SDK-HMAC-SHA256"),
            fixture(
                "derived-post",
                derived,
                "V11-HMAC-SHA256",
                regionId="test-region-1",
                serviceName="demo",
            ),
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(generate(), indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

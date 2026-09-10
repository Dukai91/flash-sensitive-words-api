"""Exercise a running local stack. Deletes only the temporary term this script creates."""
import argparse
import json
import time
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--url", default="http://localhost:8080")
args = parser.parse_args()
base = args.url.rstrip("/")


def request(method, path, body=None, expected=200, raw=False):
    payload = body.encode() if raw else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(base + path, data=payload, method=method,
                                 headers={"Content-Type": "application/json"} if payload is not None else {})
    try:
        response = urllib.request.urlopen(req, timeout=25)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status
        data = response.read()
    assert status == expected, (method, path, status, expected, data[:300])
    return json.loads(data) if data else None


for attempt in range(60):
    try:
        request("GET", "/actuator/health/readiness")
        break
    except (AssertionError, OSError):
        if attempt == 59:
            raise
        time.sleep(1)

assert request("POST", "/api/v1/sanitize", {"text": "SELECT * FROM sensitiveWords"})["sanitized"] == "****** * FROM sensitiveWords"
for invalid in ('{"text":123}', '{"text":true}', '{"text":"x","text":"y"}', '{"text":"x"} {"text":"y"}'):
    request("POST", "/api/v1/sanitize", invalid, expected=400, raw=True)
request("GET", "/api/v1/internal/sensitive-words?page=2147483647&size=100", expected=400)
request("POST", "/api/v1/sanitize", {"text": "\u00a0"}, expected=400)

term = "İ" + uuid.uuid4().hex
path = "/api/v1/internal/sensitive-words"
created = request("POST", path, {"word": term}, expected=201)
resource = path + "/" + str(created["id"])
try:
    assert request("POST", "/api/v1/sanitize", {"text": term})["sanitized"] == "*" * len(term)
    request("POST", path, {"word": "i" + term[1:]}, expected=409)
    fetched = request("GET", resource)
    assert fetched["createdAt"] == created["createdAt"]
    renamed = "renamed" + uuid.uuid4().hex
    updated = request("PUT", resource, {"word": renamed})
    assert request("GET", resource) == updated
    assert request("POST", "/api/v1/sanitize", {"text": renamed})["sanitized"] == "*" * len(renamed)
finally:
    request("DELETE", resource, expected=204)
request("GET", resource, expected=404)
spec = request("GET", "/v3/api-docs")
assert spec["components"]["schemas"]["SanitizeRequest"]["properties"]["text"]["maxLength"] > 0
assert "Location" in spec["paths"][path]["post"]["responses"]["201"]["headers"]
print("PASS: readiness, Flash example, strict JSON, pagination, Unicode CRUD, timestamps, refresh and OpenAPI")

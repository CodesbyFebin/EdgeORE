# Gateway behind a real HTTPS proxy (local test harness, not a hosted deployment)

`scripts/gateway-proxy-it.sh` on EdgeORE's build host, at the commit that added it (see the commit log). Everything ran
on loopback (Caddy on 127.0.0.1:18443, the gateway on 127.0.0.1:19844) with a throwaway self-signed certificate and
token that were deleted when the script exited. Nothing was deployed or exposed to a network.

- Caddy v2.11.7, the official linux/amd64 release, verified against the SHA-512 in its release checksums file (this
  checks integrity, not the publisher's signature). Caddy is a box tool here, not a project dependency.
- Config: `services/storage-gateway/Caddyfile.example` (TLS 1.2/1.3 with the given cert, 413 for a declared
  Content-Length above 8,001,024 bytes, `request_body max_size` as a backstop, header/body/idle timeouts, only the
  documented `/v1/...` routes forwarded, no access log, no admin endpoint).
- `gateway-proxy-it.log`: 14 curl checks (TLS refused without trust and with a wrong pin; 401 with no or a wrong token;
  upload, status, download with matching bytes; 409 quota; 413 at the proxy; 404 for an undocumented path; cancel then
  404). Then the JVM suite `GatewayProxyIntegrationTest` (1 test) ran the app's `OwnedGatewayBackupProvider` with the
  certificate's SPKI pin: no pin and a wrong pin fail in the handshake, a wrong token gets 401, and upload, idempotent
  re-upload, status, download, quota 409, cancel and post-cancel 404 all behave as specified.
- `throwaway-cert-spki-pin.txt` is the pin of the deleted certificate, for traceability only.

First run: the oversize upload got 502, not 413. Caddy's `request_body max_size` cuts a proxied body off mid-stream.
The Caddyfile now answers a declared Content-Length over the limit with 413 before forwarding; a body without a
Content-Length is still cut off as 502 (and the gateway refuses chunked uploads anyway).

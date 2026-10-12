# Prototype GHCR gateway

Build from the repository root:

    docker build -f services/storage-gateway/Dockerfile --build-arg SOURCE_REVISION=$(git rev-parse HEAD) -t edgeore-storage-gateway:review .

Create a private environment file outside the repository containing EDGEORE_STORAGE_TOKEN
(a random secret of at least 32 characters). Do not put the credential in an image or APK.

    docker volume create edgeore-storage
    docker run -d --name edgeore-storage --read-only --cap-drop=ALL --security-opt=no-new-privileges --tmpfs /tmp:rw,noexec,nosuid,size=16m --env-file /private/gateway.env -p 127.0.0.1:19844:19844 -v edgeore-storage:/data edgeore-storage-gateway:review

The image runs as UID/GID 10001 and initializes its named volume with that ownership.
Existing bind mounts need owner-managed UID 10001 permissions. SQLite persists in /data.
Missing credentials refuse startup. The process binds all container interfaces; publish only
on host loopback and place an owner-managed HTTPS reverse proxy in front. Direct Python
execution still defaults to loopback. See [integration](../../docs/storage/INTEGRATION.md).
Use a filesystem quota in addition to the retained-payload quota; cancellation is not deletion.
No health endpoint bypasses authentication. This is a single-credential prototype, without
production tenant isolation, scoped auth, disaster recovery or settlement. Registry publication
does not qualify Android remote backup, TLS, device restore or payouts. License choice remains
pending; no open-source license is asserted. Preserve NOTICE and base-image licenses.

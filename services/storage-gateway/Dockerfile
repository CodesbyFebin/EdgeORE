FROM python:3.12-slim-bookworm@sha256:34386ef0cb081344d7ec1c103ba398e6e9f64e9ab3a1509accc92a4e24a07258
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.source="https://github.com/CodesbyFebin/EdgeORE" \
      org.opencontainers.image.revision="$SOURCE_REVISION" \
      org.opencontainers.image.licenses="NOASSERTION" \
      org.opencontainers.image.description="EdgeORE prototype ciphertext gateway; owner-managed HTTPS required"
RUN mkdir /data && chown 10001:10001 /data
WORKDIR /app
COPY --chmod=0444 services/storage-gateway/server.py /app/server.py
COPY --chmod=0444 NOTICE /app/NOTICE
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 EDGEORE_STORAGE_BIND=0.0.0.0 EDGEORE_STORAGE_DB=/data/storage.sqlite3
USER 10001:10001
EXPOSE 19844
CMD ["python", "/app/server.py"]

#!/bin/sh
set -e

mc alias set local http://minio:38060 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
mc mb local/"${MINIO_BUCKET}" --ignore-existing

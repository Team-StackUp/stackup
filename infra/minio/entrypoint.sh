#!/bin/sh
set -e

# Overridden listen ports — keep host:container symmetric across StackUp (38XXX).
exec minio server /data --address ":38060" --console-address ":38061"

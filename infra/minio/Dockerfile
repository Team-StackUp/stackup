FROM minio/minio:latest

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 38060 38061

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

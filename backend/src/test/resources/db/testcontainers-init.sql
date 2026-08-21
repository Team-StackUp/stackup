-- infra/postgres/init.sql 과 같은 내용. 마이그레이션(V*)이 pgvector 타입을 쓰므로
-- Flyway 가 돌기 전에 확장이 있어야 한다.
CREATE EXTENSION IF NOT EXISTS vector;

-- Runtime PostgreSQL/pgvector schema used by the current Spring JDBC application.
-- V1 keeps a normalized experiment draft (`chunks`, `chunk_embeddings`).
-- V2 aligns the live runtime tables (`documents`, `document_chunks`) with pgvector retrieval.

create extension if not exists vector;

create table if not exists documents (
    id bigserial primary key,
    title varchar(255) not null,
    source_path varchar(512) not null,
    content_type varchar(50) not null,
    url varchar(1024),
    posted_at timestamp,
    created_at timestamp not null default current_timestamp
);

alter table documents add column if not exists index_name varchar(120);
alter table documents add column if not exists corpus_profile varchar(50);
alter table documents add column if not exists index_version varchar(100);
alter table documents add column if not exists url varchar(1024);
alter table documents add column if not exists posted_at timestamp;

create table if not exists document_chunks (
    id bigserial primary key,
    document_id bigint not null references documents(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    page_number integer,
    section_name varchar(255),
    embedding text not null,
    embedding_model varchar(120),
    embedding_dim integer,
    index_version varchar(100),
    created_at timestamp not null default current_timestamp
);

alter table document_chunks add column if not exists embedding_model varchar(120);
alter table document_chunks add column if not exists embedding_dim integer;
alter table document_chunks add column if not exists index_version varchar(100);
alter table document_chunks add column if not exists embedding_vector vector(256);

create index if not exists idx_runtime_documents_index
    on documents (index_name, corpus_profile, index_version);

create index if not exists idx_runtime_document_chunks_version
    on document_chunks (index_version, embedding_model, embedding_dim);

create index if not exists idx_runtime_document_chunks_embedding_cosine
    on document_chunks using hnsw (embedding_vector vector_cosine_ops);

alter table index_metadata add column if not exists index_name varchar(120);
alter table index_metadata add column if not exists retrieval_config_hash varchar(64);
alter table index_metadata add column if not exists source_data_path varchar(512);

create index if not exists idx_index_metadata_name_version
    on index_metadata (index_name, index_version, created_at desc);

create index if not exists idx_retrieval_logs_version
    on retrieval_logs (index_version, retrieval_method, created_at desc);

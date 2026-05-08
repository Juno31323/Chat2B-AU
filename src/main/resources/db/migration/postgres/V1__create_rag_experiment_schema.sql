-- Draft PostgreSQL/Supabase migration for paper experiments.
-- Apply manually or wire through Flyway before running experiment/production profiles.

create extension if not exists vector;

create table if not exists documents (
    id bigserial primary key,
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    title varchar(255) not null,
    source_path varchar(512) not null,
    url varchar(1024),
    posted_at timestamp,
    content_type varchar(50) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists chunks (
    id bigserial primary key,
    document_id bigint not null references documents(id) on delete cascade,
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    chunk_index integer not null,
    content text not null,
    page_number integer,
    section_name varchar(255),
    created_at timestamp not null default current_timestamp
);

create table if not exists chunk_embeddings (
    id bigserial primary key,
    chunk_id bigint not null references chunks(id) on delete cascade,
    document_id bigint not null references documents(id) on delete cascade,
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    embedding_model varchar(120) not null,
    embedding_dim integer not null,
    embedding vector(256) not null,
    created_at timestamp not null default current_timestamp,
    unique (chunk_id, embedding_model, embedding_dim, index_version)
);

create index if not exists idx_documents_profile_version
    on documents (corpus_profile, index_version);

create index if not exists idx_chunks_profile_version
    on chunks (corpus_profile, index_version);

create index if not exists idx_chunk_embeddings_profile_version
    on chunk_embeddings (corpus_profile, index_version, embedding_model, embedding_dim);

create index if not exists idx_chunk_embeddings_embedding_cosine
    on chunk_embeddings using hnsw (embedding vector_cosine_ops);

create table if not exists index_metadata (
    id bigserial primary key,
    index_name varchar(120) not null,
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    document_count integer not null,
    chunk_count integer not null,
    embedding_model varchar(120) not null,
    embedding_dim integer not null,
    chunk_size integer not null,
    chunk_overlap integer not null,
    tokenizer varchar(120) not null,
    retrieval_method varchar(80) not null,
    corpus_hash varchar(64) not null,
    retrieval_config_hash varchar(64) not null,
    source_data_path varchar(512) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists retrieval_logs (
    id bigserial primary key,
    run_id varchar(120),
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    question text not null,
    chunk_id bigint,
    document_id bigint,
    title varchar(255),
    url varchar(1024),
    posted_at timestamp,
    dense_score double precision,
    bm25_score double precision,
    hybrid_score double precision,
    bm25_rank integer,
    dense_rank integer,
    hybrid_rank integer,
    fusion_method varchar(80),
    retrieval_mode varchar(80) not null,
    created_at timestamp not null default current_timestamp
);

create table if not exists bm25_index_metadata (
    id bigserial primary key,
    corpus_profile varchar(50) not null,
    index_version varchar(100) not null,
    tokenizer varchar(120) not null,
    document_count integer not null,
    chunk_count integer not null,
    corpus_hash varchar(64) not null,
    created_at timestamp not null default current_timestamp
);

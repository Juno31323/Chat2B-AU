-- Paper-first experiment metadata.
-- This migration keeps already-created PostgreSQL/Supabase databases aligned with
-- the revised manuscript without rebuilding the whole schema.

alter table documents add column if not exists notice_id varchar(160);
alter table documents add column if not exists collected_at timestamp;
alter table documents add column if not exists department varchar(160);
alter table documents add column if not exists category varchar(120);
alter table documents add column if not exists attachment_urls text;
alter table documents add column if not exists local_file_path varchar(1024);
alter table documents add column if not exists source_hash varchar(64);

alter table chunks add column if not exists source_image_path varchar(1024);
alter table chunks add column if not exists ocr_engine varchar(120);
alter table chunks add column if not exists preprocess_profile varchar(80);
alter table chunks add column if not exists block_type varchar(50);
alter table chunks add column if not exists bbox jsonb;
alter table chunks add column if not exists reading_order integer;
alter table chunks add column if not exists confidence double precision;
alter table chunks add column if not exists extracted_dates jsonb;
alter table chunks add column if not exists extracted_contacts jsonb;

alter table retrieval_logs add column if not exists question_id varchar(120);
alter table retrieval_logs add column if not exists notice_id varchar(160);

create table if not exists experiment_runs (
    run_id varchar(120) primary key,
    method varchar(80) not null,
    config_file varchar(512) not null,
    index_name varchar(120) not null,
    index_version varchar(100) not null,
    question_set varchar(512) not null,
    generation_provider varchar(50),
    generation_model varchar(120),
    prompt_version varchar(120),
    started_at timestamp not null default current_timestamp,
    finished_at timestamp,
    notes text
);

create table if not exists evaluation_questions (
    question_id varchar(120) primary key,
    question text not null,
    question_type varchar(80),
    source_type varchar(80),
    gold_notice_id varchar(160),
    gold_title varchar(255),
    gold_answer text,
    allowed_answer text,
    required_field varchar(80),
    difficulty varchar(40),
    memo text
);

create table if not exists prediction_results (
    id bigserial primary key,
    run_id varchar(120),
    question_id varchar(120),
    method varchar(80) not null,
    answer text,
    refused boolean not null default false,
    top1_notice_id varchar(160),
    top1_title varchar(255),
    retrieved_notice_ids text,
    source_urls text,
    latency_ms double precision,
    input_tokens integer,
    output_tokens integer,
    total_tokens integer,
    estimated_cost_usd double precision,
    created_at timestamp not null default current_timestamp
);

create table if not exists metric_results (
    id bigserial primary key,
    run_id varchar(120),
    method varchar(80) not null,
    subset varchar(80) not null,
    recall_at_1 double precision,
    recall_at_3 double precision,
    recall_at_5 double precision,
    mrr double precision,
    ndcg_at_5 double precision,
    answer_accuracy double precision,
    faithfulness double precision,
    answer_relevance double precision,
    source_accuracy double precision,
    hallucination_rate double precision,
    context_precision double precision,
    date_accuracy double precision,
    place_accuracy double precision,
    contact_accuracy double precision,
    target_accuracy double precision,
    table_qa_accuracy double precision,
    refusal_accuracy double precision,
    avg_latency_ms double precision,
    note text,
    created_at timestamp not null default current_timestamp
);

create table if not exists error_analysis_results (
    id bigserial primary key,
    run_id varchar(120),
    question_id varchar(120),
    method varchar(80) not null,
    error_type varchar(120) not null,
    error_detail text,
    suspected_cause text,
    fix_idea text,
    memo text,
    created_at timestamp not null default current_timestamp
);

create table if not exists ocr_quality_results (
    id bigserial primary key,
    sample_id varchar(120) not null,
    notice_id varchar(160),
    image_path varchar(1024) not null,
    ocr_engine varchar(120) not null,
    preprocess_profile varchar(80) not null,
    title_correct boolean,
    date_correct boolean,
    contact_correct boolean,
    place_correct boolean,
    table_preserved boolean,
    reading_order_correct boolean,
    avg_confidence double precision,
    error_note text,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_documents_notice_id
    on documents (notice_id);

create index if not exists idx_documents_source_hash
    on documents (source_hash);

create index if not exists idx_retrieval_logs_notice_id
    on retrieval_logs (notice_id);

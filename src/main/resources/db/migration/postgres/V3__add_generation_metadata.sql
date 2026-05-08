-- Generation metadata and token usage for reproducible paper experiment runs.

alter table chat_logs add column if not exists generation_provider varchar(50);
alter table chat_logs add column if not exists generation_model varchar(120);
alter table chat_logs add column if not exists generation_model_version varchar(160);
alter table chat_logs add column if not exists generation_temperature double precision;
alter table chat_logs add column if not exists generation_max_output_tokens integer;
alter table chat_logs add column if not exists prompt_version varchar(120);
alter table chat_logs add column if not exists input_tokens integer;
alter table chat_logs add column if not exists output_tokens integer;
alter table chat_logs add column if not exists total_tokens integer;
alter table chat_logs add column if not exists estimated_cost_usd double precision;

alter table retrieval_logs add column if not exists generation_provider varchar(50);
alter table retrieval_logs add column if not exists generation_model varchar(120);
alter table retrieval_logs add column if not exists prompt_version varchar(120);

create index if not exists idx_chat_logs_generation_model
    on chat_logs (generation_provider, generation_model, prompt_version, created_at desc);

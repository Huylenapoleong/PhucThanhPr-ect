begin;

create table if not exists public.outbox_events (
    event_id uuid primary key,
    aggregate_type text not null,
    aggregate_id text not null,
    event_type text not null,
    event_version integer not null default 1,
    payload jsonb not null,
    correlation_id text,
    status text not null default 'pending',
    retry_count integer not null default 0,
    next_retry_at timestamptz,
    occurred_at timestamptz not null default now(),
    published_at timestamptz,
    last_error text,
    constraint outbox_events_status_check
        check (status in ('pending', 'published', 'failed')),
    constraint outbox_events_retry_count_check
        check (retry_count >= 0),
    constraint outbox_events_payload_object_check
        check (jsonb_typeof(payload) = 'object')
);

create index if not exists outbox_events_pending_idx
    on public.outbox_events (occurred_at)
    where status in ('pending', 'failed');

create index if not exists outbox_events_aggregate_idx
    on public.outbox_events (aggregate_type, aggregate_id, occurred_at);

create table if not exists public.processed_events (
    event_id uuid not null,
    consumer_name text not null,
    processed_at timestamptz not null default now(),
    primary key (event_id, consumer_name)
);

alter table public.outbox_events enable row level security;
alter table public.processed_events enable row level security;

revoke all on table public.outbox_events from anon, authenticated;
revoke all on table public.processed_events from anon, authenticated;

grant select, insert, update on table public.outbox_events to service_role;
grant select, insert on table public.processed_events to service_role;

commit;

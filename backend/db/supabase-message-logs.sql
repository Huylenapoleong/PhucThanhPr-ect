begin;

create table if not exists public.message_logs (
    id bigint generated always as identity primary key,
    message_code text not null unique,
    customer_id bigint references public.customers (id),
    reminder_id bigint references public.customer_reminders (id),
    channel text not null,
    direction text not null default 'outbound',
    recipient text not null,
    subject text,
    content text not null,
    status text not null default 'queued',
    external_message_id text,
    provider text,
    idempotency_key text,
    sent_at timestamptz,
    delivered_at timestamptz,
    failed_at timestamptz,
    error_message text,
    provider_payload jsonb not null default '{}'::jsonb,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint message_logs_channel_check
        check (channel in ('telegram', 'zalo', 'facebook', 'email', 'sms')),
    constraint message_logs_direction_check
        check (direction in ('inbound', 'outbound')),
    constraint message_logs_status_check
        check (status in ('queued', 'sending', 'sent', 'delivered', 'failed', 'cancelled')),
    constraint message_logs_content_check check (btrim(content) <> ''),
    constraint message_logs_recipient_check check (btrim(recipient) <> ''),
    constraint message_logs_payload_check check (jsonb_typeof(provider_payload) = 'object')
);

create unique index if not exists message_logs_idempotency_unique_idx
    on public.message_logs (idempotency_key)
    where idempotency_key is not null;

create index if not exists message_logs_status_created_idx
    on public.message_logs (status, created_at);

alter table public.message_logs enable row level security;
revoke all on table public.message_logs from anon, authenticated;
grant select, insert, update on table public.message_logs to service_role;
grant usage, select on sequence public.message_logs_id_seq to service_role;

commit;

-- Phuc Thanh Audio Group
-- Bang thu bay: customer_reminders
-- Chay sau supabase-warranty-repairs.sql.
-- Muc tieu: CSKH tu dong - nhac no, bao hanh, sinh nhat, follow-up va thong bao sua chua.

begin;

create table if not exists public.customer_reminders (
    id bigint generated always as identity primary key,

    -- Ma lich nhac do Spring Boot sinh, vi du: NH-000001
    reminder_code text not null,

    customer_id bigint not null references public.customers (id),

    -- Cac lien ket ngu canh; nullable de mot lich nhac co the chi gan voi customer.
    lead_id bigint references public.leads (id),
    contract_id bigint references public.contracts (id),
    customer_asset_id bigint references public.customer_assets (id),
    repair_request_id bigint references public.repair_requests (id),

    reminder_type text not null,
    title text not null,
    message text,

    due_at timestamptz not null,
    assigned_to_user_id uuid,
    priority text not null default 'normal',

    channel text not null default 'zalo',
    recipient_name text,
    recipient_phone text,
    recipient_email text,

    status text not null default 'pending',
    sent_at timestamptz,
    done_at timestamptz,
    cancelled_at timestamptz,
    failed_at timestamptz,
    next_retry_at timestamptz,
    retry_count integer not null default 0,

    external_message_id text,
    error_message text,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint customer_reminders_reminder_code_unique unique (reminder_code),
    constraint customer_reminders_reminder_code_format_check
        check (
            btrim(reminder_code) <> ''
            and reminder_code = upper(btrim(reminder_code))
        ),
    constraint customer_reminders_title_not_blank_check
        check (btrim(title) <> ''),
    constraint customer_reminders_type_check
        check (
            reminder_type in (
                'debt',
                'warranty',
                'event',
                'birthday',
                'follow_up',
                'repair_completed',
                'contract_renewal',
                'payment_due'
            )
        ),
    constraint customer_reminders_priority_check
        check (priority in ('low', 'normal', 'high', 'urgent')),
    constraint customer_reminders_channel_check
        check (channel in ('telegram', 'zalo', 'facebook', 'email', 'call', 'sms', 'manual')),
    constraint customer_reminders_recipient_phone_not_blank_check
        check (
            recipient_phone is null
            or (btrim(recipient_phone) <> '' and recipient_phone = btrim(recipient_phone))
        ),
    constraint customer_reminders_recipient_email_not_blank_check
        check (
            recipient_email is null
            or (btrim(recipient_email) <> '' and recipient_email = btrim(recipient_email))
        ),
    constraint customer_reminders_status_check
        check (status in ('pending', 'sent', 'done', 'cancelled', 'failed', 'snoozed')),
    constraint customer_reminders_status_dates_check
        check (
            (status in ('sent', 'done') or sent_at is null)
            and (status = 'done' or done_at is null)
            and (status = 'cancelled' or cancelled_at is null)
            and (status = 'failed' or failed_at is null)
            and (sent_at is null or sent_at >= created_at)
            and (done_at is null or done_at >= created_at)
            and (cancelled_at is null or cancelled_at >= created_at)
            and (failed_at is null or failed_at >= created_at)
            and (next_retry_at is null or status in ('pending', 'failed', 'snoozed'))
        ),
    constraint customer_reminders_retry_count_non_negative_check
        check (retry_count >= 0),
    constraint customer_reminders_error_only_when_failed_check
        check (status = 'failed' or error_message is null),
    constraint customer_reminders_version_non_negative_check
        check (version >= 0)
);

comment on table public.customer_reminders is
    'Lich CSKH tu dong: nhac no, bao hanh, sinh nhat, follow-up, thong bao sua chua.';
comment on column public.customer_reminders.customer_id is
    'Khach hang can nhac/thong bao.';
comment on column public.customer_reminders.contract_id is
    'Hop dong lien quan, vi du nhac no hoac gia han.';
comment on column public.customer_reminders.customer_asset_id is
    'Thiet bi lien quan, vi du sap het bao hanh.';
comment on column public.customer_reminders.repair_request_id is
    'Phieu sua chua lien quan, vi du thong bao khach da sua xong.';
comment on column public.customer_reminders.assigned_to_user_id is
    'UUID nhan vien phu trach; FK duoc bo sung trong supabase-staff-members.sql.';
comment on column public.customer_reminders.external_message_id is
    'ID tin nhan tu Zalo/Facebook/Telegram/email provider neu co.';

-- Man hinh web CSKH: cac viec can xu ly theo trang thai va han nhac.
create index if not exists customer_reminders_status_due_idx
    on public.customer_reminders (status, due_at);

-- Lich nhac theo khach.
create index if not exists customer_reminders_customer_id_idx
    on public.customer_reminders (customer_id);

-- Nhac lien quan lead/pipeline.
create index if not exists customer_reminders_lead_id_idx
    on public.customer_reminders (lead_id)
    where lead_id is not null;

-- Nhac lien quan hop dong/cong no.
create index if not exists customer_reminders_contract_id_idx
    on public.customer_reminders (contract_id)
    where contract_id is not null;

-- Nhac bao hanh theo thiet bi.
create index if not exists customer_reminders_customer_asset_id_idx
    on public.customer_reminders (customer_asset_id)
    where customer_asset_id is not null;

-- Nhac lien quan phieu sua chua.
create index if not exists customer_reminders_repair_request_id_idx
    on public.customer_reminders (repair_request_id)
    where repair_request_id is not null;

-- Hang doi theo nhan vien phu trach.
create index if not exists customer_reminders_assigned_status_idx
    on public.customer_reminders (assigned_to_user_id, status, due_at)
    where assigned_to_user_id is not null;

-- Job retry tin nhan loi.
create index if not exists customer_reminders_next_retry_idx
    on public.customer_reminders (next_retry_at)
    where status in ('pending', 'failed', 'snoozed')
      and next_retry_at is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_customer_reminders_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'customer_reminders_set_updated_at'
          and tgrelid = 'public.customer_reminders'::regclass
          and not tgisinternal
    ) then
        create trigger customer_reminders_set_updated_at
        before update on public.customer_reminders
        for each row
        execute function public.set_customer_reminders_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.customer_reminders enable row level security;

revoke all on table public.customer_reminders from anon, authenticated;

grant select, insert, update on table public.customer_reminders to service_role;
grant usage, select on sequence public.customer_reminders_id_seq to service_role;

revoke all on function public.set_customer_reminders_updated_at() from public;
grant execute on function public.set_customer_reminders_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'customer_reminders'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.customer_reminders (
--     reminder_code,
--     customer_id,
--     contract_id,
--     reminder_type,
--     title,
--     message,
--     due_at,
--     priority,
--     channel,
--     recipient_name,
--     recipient_phone,
--     status
-- ) values (
--     'NH-000001',
--     1,
--     1,
--     'payment_due',
--     'Nhac thanh toan hop dong HD-000001',
--     'Anh/chi vui long thanh toan theo tien do hop dong.',
--     now() + interval '1 day',
--     'high',
--     'zalo',
--     'Nguyen Van A',
--     '0901234567',
--     'pending'
-- );

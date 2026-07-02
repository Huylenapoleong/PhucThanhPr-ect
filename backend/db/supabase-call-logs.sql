-- Phuc Thanh Audio Group
-- Bang thu tam: call_logs
-- Chay sau supabase-customer-reminders.sql.
-- Muc tieu: tong dai AI 24/7, IVR bam phim, AI hieu nhu cau, chuyen nguoi that khi can.

begin;

create table if not exists public.call_logs (
    id bigint generated always as identity primary key,

    -- Ma cuoc goi do Spring Boot sinh, vi du: CG-000001
    call_code text not null,

    -- ID cuoc goi tu nha cung cap tong dai/AI voice neu co.
    external_call_id text,
    call_provider text not null default 'other',
    source_channel text not null default 'phone',

    customer_id bigint references public.customers (id),
    lead_id bigint references public.leads (id),
    repair_request_id bigint references public.repair_requests (id),
    contract_id bigint references public.contracts (id),
    customer_reminder_id bigint references public.customer_reminders (id),

    direction text not null default 'inbound',
    phone_number text not null,
    caller_name text,

    started_at timestamptz not null default now(),
    answered_at timestamptz,
    ended_at timestamptz,
    duration_seconds integer not null default 0,
    status text not null default 'queued',

    -- Cach khach tuong tac: bam phim, noi tu nhien, hoac ket hop ca hai.
    interaction_mode text not null default 'ivr_voice',
    ivr_digit text,
    route text not null default 'ai_reception',

    -- Ket qua AI nhan dien tu noi dung noi chuyen.
    intent text,
    intent_confidence numeric(5, 2),
    customer_requirement text,
    transcript text,
    ai_summary text,
    ai_resolution text,

    result text not null default 'pending',
    handoff_required boolean not null default false,
    transfer_reason text,
    transferred_to_user_id uuid,
    handled_by_user_id uuid,

    recording_url text,
    external_payload jsonb not null default '{}'::jsonb,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint call_logs_call_code_unique unique (call_code),
    constraint call_logs_external_call_id_unique unique (external_call_id),
    constraint call_logs_call_code_format_check
        check (
            btrim(call_code) <> ''
            and call_code = upper(btrim(call_code))
        ),
    constraint call_logs_external_call_id_not_blank_check
        check (
            external_call_id is null
            or (btrim(external_call_id) <> '' and external_call_id = btrim(external_call_id))
        ),
    constraint call_logs_call_provider_check
        check (
            call_provider in (
                'twilio',
                'stringee',
                'omicall',
                'cloudfone',
                'viettel',
                'fpt_ai',
                'zalo',
                'manual',
                'other'
            )
        ),
    constraint call_logs_source_channel_check
        check (source_channel in ('web_call', 'phone', 'zalo', 'facebook', 'manual', 'other')),
    constraint call_logs_direction_check
        check (direction in ('inbound', 'outbound')),
    constraint call_logs_phone_number_not_blank_check
        check (btrim(phone_number) <> '' and phone_number = btrim(phone_number)),
    constraint call_logs_status_check
        check (status in ('queued', 'ringing', 'in_progress', 'completed', 'missed', 'failed', 'cancelled')),
    constraint call_logs_duration_seconds_non_negative_check
        check (duration_seconds >= 0),
    constraint call_logs_time_order_check
        check (
            (answered_at is null or answered_at >= started_at)
            and (ended_at is null or ended_at >= started_at)
            and (ended_at is null or answered_at is null or ended_at >= answered_at)
        ),
    constraint call_logs_interaction_mode_check
        check (interaction_mode in ('ivr', 'voice_agent', 'ivr_voice', 'human', 'manual')),
    constraint call_logs_ivr_digit_check
        check (ivr_digit is null or ivr_digit in ('0', '1', '2', '3', '4', '5', '*', '#')),
    constraint call_logs_route_check
        check (
            route in (
                'ai_reception',
                'sales_quote',
                'warranty_repair',
                'accounting_debt',
                'operator',
                'general_support',
                'unknown',
                'other'
            )
        ),
    constraint call_logs_intent_not_blank_check
        check (
            intent is null
            or (btrim(intent) <> '' and intent = btrim(intent))
        ),
    constraint call_logs_intent_confidence_check
        check (intent_confidence is null or intent_confidence between 0 and 100),
    constraint call_logs_result_check
        check (
            result in (
                'pending',
                'ai_resolved',
                'lead_created',
                'repair_created',
                'reminder_created',
                'transferred',
                'callback_required',
                'missed',
                'failed',
                'no_answer',
                'spam'
            )
        ),
    constraint call_logs_result_relation_check
        check (
            (result <> 'lead_created' or lead_id is not null)
            and (result <> 'repair_created' or repair_request_id is not null)
            and (result <> 'reminder_created' or customer_reminder_id is not null)
        ),
    constraint call_logs_external_payload_object_check
        check (jsonb_typeof(external_payload) = 'object'),
    constraint call_logs_version_non_negative_check
        check (version >= 0)
);

comment on table public.call_logs is
    'Nhat ky tong dai AI/IVR: cuoc goi web/phone, transcript, intent, route va ket qua xu ly.';
comment on column public.call_logs.external_call_id is
    'ID cuoc goi tu nha cung cap nhu Twilio, Stringee, OmiCall, CloudFone, Viettel, FPT AI.';
comment on column public.call_logs.source_channel is
    'Kenh phat sinh cuoc goi: nut goi tren web, hotline phone, Zalo/Facebook hoac nhap tay.';
comment on column public.call_logs.lead_id is
    'Lead duoc AI tao hoac lien ket khi khach can tu van/bao gia.';
comment on column public.call_logs.repair_request_id is
    'Phieu bao hanh/sua chua duoc AI tao hoac lien ket khi khach bao loi.';
comment on column public.call_logs.contract_id is
    'Hop dong lien quan neu khach hoi cong no, thanh toan, tien do.';
comment on column public.call_logs.customer_reminder_id is
    'Lich nhac/callback lien quan neu AI tao lich goi lai hoac nhac viec.';
comment on column public.call_logs.interaction_mode is
    'ivr = bam phim, voice_agent = noi tu nhien, ivr_voice = ket hop bam phim va noi tu nhien.';
comment on column public.call_logs.ivr_digit is
    'Phim khach bam: 1 sales/bao gia, 2 bao hanh-sua chua, 3 cong no-ke toan, 0 gap nhan vien.';
comment on column public.call_logs.route is
    'Nhanh nghiep vu sau khi tong dai/AI phan loai yeu cau.';
comment on column public.call_logs.intent is
    'Nhu cau AI nhan dien tu loi noi cua khach, vi du bao_gia, bao_hanh, cong_no, gap_nhan_vien.';
comment on column public.call_logs.handoff_required is
    'True khi can chuyen nhan vien that xu ly tiep.';
comment on column public.call_logs.external_payload is
    'Payload goc tu nha cung cap tong dai/AI voice de doi soat khi can.';

-- Tim tat ca cuoc goi cua mot khach hang.
create index if not exists call_logs_customer_id_idx
    on public.call_logs (customer_id)
    where customer_id is not null;

-- Cuoc goi lien quan pipeline/bao gia.
create index if not exists call_logs_lead_id_idx
    on public.call_logs (lead_id)
    where lead_id is not null;

-- Cuoc goi lien quan bao hanh/sua chua.
create index if not exists call_logs_repair_request_id_idx
    on public.call_logs (repair_request_id)
    where repair_request_id is not null;

-- Cuoc goi lien quan hop dong/cong no.
create index if not exists call_logs_contract_id_idx
    on public.call_logs (contract_id)
    where contract_id is not null;

-- Cuoc goi lien quan lich nhac/callback.
create index if not exists call_logs_customer_reminder_id_idx
    on public.call_logs (customer_reminder_id)
    where customer_reminder_id is not null;

-- Man hinh lich su tong dai theo thoi gian moi nhat.
create index if not exists call_logs_started_at_idx
    on public.call_logs (started_at desc);

-- Tra cuu nhanh theo so dien thoai.
create index if not exists call_logs_phone_number_idx
    on public.call_logs (phone_number);

-- Hang doi xu ly theo trang thai.
create index if not exists call_logs_status_started_idx
    on public.call_logs (status, started_at desc);

-- Loc theo nhanh nghiep vu: sales, bao hanh, ke toan, operator.
create index if not exists call_logs_route_status_idx
    on public.call_logs (route, status, started_at desc);

-- Hang doi can nguoi that tiep nhan.
create index if not exists call_logs_handoff_queue_idx
    on public.call_logs (route, started_at desc)
    where handoff_required = true
      and status in ('queued', 'ringing', 'in_progress', 'completed');

-- Danh sach can goi lai.
create index if not exists call_logs_callback_required_idx
    on public.call_logs (started_at desc)
    where result = 'callback_required';

-- Tu dong cap nhat updated_at.
create or replace function public.set_call_logs_updated_at()
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
        where tgname = 'call_logs_set_updated_at'
          and tgrelid = 'public.call_logs'::regclass
          and not tgisinternal
    ) then
        create trigger call_logs_set_updated_at
        before update on public.call_logs
        for each row
        execute function public.set_call_logs_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.call_logs enable row level security;

revoke all on table public.call_logs from anon, authenticated;

grant select, insert, update on table public.call_logs to service_role;
grant usage, select on sequence public.call_logs_id_seq to service_role;

revoke all on function public.set_call_logs_updated_at() from public;
grant execute on function public.set_call_logs_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'call_logs'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.call_logs (
--     call_code,
--     source_channel,
--     phone_number,
--     caller_name,
--     interaction_mode,
--     ivr_digit,
--     route,
--     intent,
--     intent_confidence,
--     customer_requirement,
--     result,
--     status
-- ) values (
--     'CG-000001',
--     'web_call',
--     '0901234567',
--     'Nguyen Van A',
--     'ivr_voice',
--     '1',
--     'sales_quote',
--     'bao_gia',
--     92.50,
--     'Khach can bao gia he thong am thanh hoi truong.',
--     'ai_resolved',
--     'completed'
-- );

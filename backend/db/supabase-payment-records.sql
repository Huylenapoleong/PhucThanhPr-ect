-- Phuc Thanh Audio Group
-- Bang thu muoi bon: payment_records
-- Chay sau supabase-invoices.sql.
-- Muc tieu: luu tung giao dich thu tien/hoan tien va doi soat cong no.
-- Backend cap nhat invoices.paid_amount va contracts.paid_amount trong cung transaction
-- khi payment chuyen sang confirmed; file nay khong tao trigger tai chinh ngam.

begin;

create table if not exists public.payment_records (
    id bigint generated always as identity primary key,

    -- Ma noi bo do Spring Boot sinh, vi du: PAY-000001.
    payment_code text not null,

    customer_id bigint not null references public.customers (id),
    contract_id bigint references public.contracts (id),
    invoice_id bigint references public.invoices (id),

    -- Refund la mot record rieng va tro ve giao dich thu tien goc.
    reversal_of_payment_id bigint references public.payment_records (id),
    direction text not null default 'receipt',

    amount numeric(18, 2) not null,
    signed_amount numeric(18, 2) generated always as (
        case
            when direction = 'refund' then -amount
            else amount
        end
    ) stored,
    currency text not null default 'VND',

    payment_method text not null default 'bank_transfer',
    source text not null default 'manual',
    status text not null default 'pending',

    paid_at timestamptz not null default now(),
    confirmed_at timestamptz,
    failed_at timestamptz,
    cancelled_at timestamptz,

    -- Doi soat webhook, ngan hang, cong thanh toan, MISA/Odoo.
    provider text,
    external_transaction_id text,
    idempotency_key text,
    bank_code text,
    payer_name text,
    payer_account text,
    receiver_account text,
    reference_text text,

    recorded_by_user_id uuid references public.staff_members (id)
        on update cascade on delete set null,

    source_payload jsonb not null default '{}'::jsonb,
    failure_reason text,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint payment_records_payment_code_unique unique (payment_code),
    constraint payment_records_payment_code_format_check
        check (
            btrim(payment_code) <> ''
            and payment_code = upper(btrim(payment_code))
        ),
    constraint payment_records_target_check
        check (contract_id is not null or invoice_id is not null),
    constraint payment_records_direction_check
        check (direction in ('receipt', 'refund')),
    constraint payment_records_refund_reference_check
        check (
            (
                direction = 'receipt'
                and reversal_of_payment_id is null
            )
            or (
                direction = 'refund'
                and reversal_of_payment_id is not null
            )
        ),
    constraint payment_records_not_self_reference_check
        check (
            reversal_of_payment_id is null
            or reversal_of_payment_id <> id
        ),
    constraint payment_records_amount_positive_check
        check (amount > 0),
    constraint payment_records_currency_check
        check (currency in ('VND', 'USD')),
    constraint payment_records_method_check
        check (
            payment_method in (
                'bank_transfer',
                'cash',
                'card',
                'payment_gateway',
                'offset',
                'other'
            )
        ),
    constraint payment_records_source_check
        check (
            source in (
                'manual',
                'bank_webhook',
                'payment_gateway',
                'misa',
                'odoo',
                'import',
                'other'
            )
        ),
    constraint payment_records_status_check
        check (status in ('pending', 'confirmed', 'failed', 'cancelled')),
    constraint payment_records_status_dates_check
        check (
            (
                status = 'confirmed'
                and confirmed_at is not null
                and failed_at is null
                and cancelled_at is null
            )
            or (
                status = 'failed'
                and confirmed_at is null
                and failed_at is not null
                and cancelled_at is null
            )
            or (
                status = 'cancelled'
                and confirmed_at is null
                and failed_at is null
                and cancelled_at is not null
            )
            or (
                status = 'pending'
                and confirmed_at is null
                and failed_at is null
                and cancelled_at is null
            )
        ),
    constraint payment_records_provider_fields_check
        check (
            (external_transaction_id is null and provider is null)
            or (
                external_transaction_id is null
                and provider is not null
                and btrim(provider) <> ''
            )
            or (
                external_transaction_id is not null
                and btrim(external_transaction_id) <> ''
                and provider is not null
                and btrim(provider) <> ''
            )
        ),
    constraint payment_records_idempotency_key_not_blank_check
        check (
            idempotency_key is null
            or (
                btrim(idempotency_key) <> ''
                and idempotency_key = btrim(idempotency_key)
            )
        ),
    constraint payment_records_source_payload_object_check
        check (jsonb_typeof(source_payload) = 'object'),
    constraint payment_records_version_non_negative_check
        check (version >= 0)
);

comment on table public.payment_records is
    'So giao dich thu tien/hoan tien; moi record la mot giao dich bat bien ve mat nghiep vu.';
comment on column public.payment_records.invoice_id is
    'Hoa don duoc thanh toan; nullable cho khoan dat coc theo hop dong.';
comment on column public.payment_records.contract_id is
    'Hop dong lien quan; bat buoc khi invoice_id khong co.';
comment on column public.payment_records.signed_amount is
    'Receipt duong, refund am; dung de tong hop paid_amount.';
comment on column public.payment_records.idempotency_key is
    'Khoa chong xu ly trung khi retry API/webhook.';
comment on column public.payment_records.source_payload is
    'Payload JSON goc tu ngan hang, gateway, MISA/Odoo hoac file import.';

-- Lich su thu tien theo khach hang.
create index if not exists payment_records_customer_paid_at_idx
    on public.payment_records (customer_id, paid_at desc);

-- FK indexes.
create index if not exists payment_records_contract_paid_at_idx
    on public.payment_records (contract_id, paid_at desc)
    where contract_id is not null;

create index if not exists payment_records_invoice_paid_at_idx
    on public.payment_records (invoice_id, paid_at desc)
    where invoice_id is not null;

create index if not exists payment_records_reversal_of_payment_id_idx
    on public.payment_records (reversal_of_payment_id)
    where reversal_of_payment_id is not null;

create index if not exists payment_records_recorded_by_user_id_idx
    on public.payment_records (recorded_by_user_id)
    where recorded_by_user_id is not null;

-- Hang doi ke toan doi soat/xac nhan.
create index if not exists payment_records_pending_idx
    on public.payment_records (paid_at, id)
    where status = 'pending';

-- Chong xu ly trung callback cua provider.
create unique index if not exists payment_records_provider_external_id_unique_idx
    on public.payment_records (provider, external_transaction_id)
    where provider is not null
      and external_transaction_id is not null;

create unique index if not exists payment_records_idempotency_key_unique_idx
    on public.payment_records (idempotency_key)
    where idempotency_key is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_payment_records_updated_at()
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
        where tgname = 'payment_records_set_updated_at'
          and tgrelid = 'public.payment_records'::regclass
          and not tgisinternal
    ) then
        create trigger payment_records_set_updated_at
        before update on public.payment_records
        for each row
        execute function public.set_payment_records_updated_at();
    end if;
end;
$$;

-- Frontend goi backend; khong cho browser doc/ghi truc tiep.
alter table public.payment_records enable row level security;

revoke all on table public.payment_records from anon, authenticated;

grant select, insert, update on table public.payment_records to service_role;
grant usage, select on sequence public.payment_records_id_seq to service_role;

revoke all on function public.set_payment_records_updated_at() from public;
grant execute on function public.set_payment_records_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'payment_records'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.payment_records (
--     payment_code,
--     customer_id,
--     contract_id,
--     invoice_id,
--     direction,
--     amount,
--     currency,
--     payment_method,
--     source,
--     status,
--     paid_at,
--     confirmed_at,
--     idempotency_key
-- )
-- values (
--     'PAY-000001',
--     1,
--     1,
--     1,
--     'receipt',
--     5000000,
--     'VND',
--     'bank_transfer',
--     'manual',
--     'confirmed',
--     now(),
--     now(),
--     'manual-PAY-000001'
-- )
-- returning id, payment_code, signed_amount, status;


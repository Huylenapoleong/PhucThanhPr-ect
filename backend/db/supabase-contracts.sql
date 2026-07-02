-- Phuc Thanh Audio Group
-- Bang thu nam: contracts
-- Chay sau supabase-quotations.sql.
-- Muc tieu: quan ly hop dong tao tu bao gia da chot.

begin;

create table if not exists public.contracts (
    id bigint generated always as identity primary key,

    -- Ma nghiep vu do Spring Boot sinh, vi du: HD-000001
    contract_code text not null,

    customer_id bigint not null references public.customers (id),

    -- Mot bao gia chi nen chuyen thanh toi da mot hop dong.
    quotation_id bigint references public.quotations (id),

    signed_date date,
    start_date date,
    end_date date,
    payment_due_date date,

    total_value numeric(18, 2) not null default 0,
    paid_amount numeric(18, 2) not null default 0,
    remaining_amount numeric(18, 2) generated always as (
        total_value - paid_amount
    ) stored,

    payment_status text not null default 'unpaid',
    status text not null default 'draft',

    -- Link Google Docs/PDF/Drive cua hop dong.
    document_url text,
    google_doc_url text,
    pdf_url text,

    -- Ma chung tu/hoa don doi chieu voi MISA.
    misa_reference text,
    notes text,

    created_by_user_id uuid,
    approved_at timestamptz,
    cancelled_at timestamptz,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint contracts_contract_code_unique unique (contract_code),
    constraint contracts_quotation_id_unique unique (quotation_id),
    constraint contracts_contract_code_format_check
        check (
            btrim(contract_code) <> ''
            and contract_code = upper(btrim(contract_code))
        ),
    constraint contracts_dates_check
        check (
            (start_date is null or end_date is null or end_date >= start_date)
            and (signed_date is null or end_date is null or end_date >= signed_date)
            and (payment_due_date is null or signed_date is null or payment_due_date >= signed_date)
        ),
    constraint contracts_money_non_negative_check
        check (
            total_value >= 0
            and paid_amount >= 0
            and paid_amount <= total_value
        ),
    constraint contracts_payment_status_check
        check (payment_status in ('unpaid', 'partial', 'paid', 'overdue')),
    constraint contracts_status_check
        check (status in ('draft', 'active', 'completed', 'cancelled')),
    constraint contracts_cancelled_at_check
        check (status = 'cancelled' or cancelled_at is null),
    constraint contracts_approved_at_check
        check (status in ('active', 'completed') or approved_at is null),
    constraint contracts_version_non_negative_check
        check (version >= 0)
);

comment on table public.contracts is
    'Hop dong phat sinh tu bao gia da duyet/chot voi khach hang.';
comment on column public.contracts.customer_id is
    'Khach hang ky hop dong.';
comment on column public.contracts.quotation_id is
    'Bao gia nguon cua hop dong; unique de mot bao gia chi tao toi da mot hop dong.';
comment on column public.contracts.remaining_amount is
    'Tu dong tinh bang total_value - paid_amount.';
comment on column public.contracts.misa_reference is
    'Ma doi chieu chung tu/hoa don/cong no trong MISA.';
comment on column public.contracts.created_by_user_id is
    'UUID nhan vien tao hop dong; FK duoc bo sung trong supabase-staff-members.sql.';

-- Tim lich su hop dong theo khach.
create index if not exists contracts_customer_id_idx
    on public.contracts (customer_id);

-- Loc hop dong theo trang thai ky/thuc hien.
create index if not exists contracts_status_signed_date_idx
    on public.contracts (status, signed_date desc);

-- Job nhac thu tien/cong no.
create index if not exists contracts_payment_due_idx
    on public.contracts (payment_status, payment_due_date)
    where payment_status in ('unpaid', 'partial', 'overdue')
      and payment_due_date is not null;

-- Tim nhanh doi chieu MISA.
create index if not exists contracts_misa_reference_idx
    on public.contracts (misa_reference)
    where misa_reference is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_contracts_updated_at()
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
        where tgname = 'contracts_set_updated_at'
          and tgrelid = 'public.contracts'::regclass
          and not tgisinternal
    ) then
        create trigger contracts_set_updated_at
        before update on public.contracts
        for each row
        execute function public.set_contracts_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.contracts enable row level security;

revoke all on table public.contracts from anon, authenticated;

grant select, insert, update on table public.contracts to service_role;
grant usage, select on sequence public.contracts_id_seq to service_role;

revoke all on function public.set_contracts_updated_at() from public;
grant execute on function public.set_contracts_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'contracts'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.contracts (
--     contract_code,
--     customer_id,
--     quotation_id,
--     signed_date,
--     start_date,
--     end_date,
--     payment_due_date,
--     total_value,
--     paid_amount,
--     payment_status,
--     status,
--     document_url,
--     misa_reference
-- ) values (
--     'HD-000001',
--     1,
--     1,
--     current_date,
--     current_date,
--     current_date + 30,
--     current_date + 15,
--     16500000,
--     0,
--     'unpaid',
--     'active',
--     'https://drive.google.com/placeholder-contract',
--     'MISA-PLACEHOLDER-001'
-- );

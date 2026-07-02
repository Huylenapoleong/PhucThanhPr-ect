-- Phuc Thanh Audio Group
-- Bang thu hai: customers
-- Chay toan bo file nay trong Supabase Dashboard > SQL Editor.
-- Tai lieu giai thich: supabase-customers-explanation.md

begin;

create table if not exists public.customers (
    id bigint generated always as identity primary key,

    -- Ma nghiep vu do Spring Boot sinh, vi du: KH-000001
    customer_code text not null,
    customer_type text not null default 'business',

    -- Ten hien thi trong CRM va thong tin phap ly.
    display_name text not null,
    legal_name text,
    tax_code text,
    legal_representative text,
    representative_title text,
    registered_address text,
    billing_address text,

    -- Lien he chinh. Neu mot cong ty co nhieu nguoi lien he,
    -- tao them bang customer_contacts o buoc sau.
    primary_contact_name text,
    primary_phone text,
    primary_email text,
    website text,

    source text not null default 'manual',

    -- Trang thai tra cuu MST.
    tax_verification_status text not null default 'not_checked',
    tax_verified_at timestamptz,
    tax_lookup_source text,
    tax_lookup_payload jsonb not null default '{}'::jsonb,

    -- ID de dong bo voi he thong ngoai.
    odoo_partner_id text,
    misa_customer_id text,

    status text not null default 'active',
    notes text,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint customers_customer_code_unique unique (customer_code),
    constraint customers_tax_code_unique unique (tax_code),
    constraint customers_odoo_partner_id_unique unique (odoo_partner_id),
    constraint customers_misa_customer_id_unique unique (misa_customer_id),

    constraint customers_customer_code_format_check
        check (
            btrim(customer_code) <> ''
            and customer_code = upper(btrim(customer_code))
        ),
    constraint customers_type_check
        check (customer_type in ('individual', 'business')),
    constraint customers_display_name_not_blank_check
        check (btrim(display_name) <> ''),
    constraint customers_tax_code_not_blank_check
        check (
            tax_code is null
            or (btrim(tax_code) <> '' and tax_code = btrim(tax_code))
        ),
    constraint customers_primary_phone_not_blank_check
        check (
            primary_phone is null
            or (btrim(primary_phone) <> '' and primary_phone = btrim(primary_phone))
        ),
    constraint customers_primary_email_not_blank_check
        check (
            primary_email is null
            or (btrim(primary_email) <> '' and primary_email = btrim(primary_email))
        ),
    constraint customers_source_check
        check (
            source in (
                'web',
                'facebook',
                'zalo',
                'call',
                'dauthau',
                'referral',
                'manual',
                'other'
            )
        ),
    constraint customers_tax_verification_status_check
        check (
            tax_verification_status in (
                'not_checked',
                'verified',
                'not_found',
                'mismatch',
                'inactive'
            )
        ),
    constraint customers_tax_lookup_payload_object_check
        check (jsonb_typeof(tax_lookup_payload) = 'object'),
    constraint customers_status_check
        check (status in ('active', 'inactive', 'blocked')),
    constraint customers_version_non_negative_check
        check (version >= 0)
);

comment on table public.customers is
    'Ho so khach hang ca nhan/doanh nghiep cua Phuc Thanh Audio Group.';
comment on column public.customers.tax_code is
    'Ma so thue; nullable cho khach ca nhan va unique khi co gia tri.';
comment on column public.customers.tax_lookup_payload is
    'Snapshot JSON du lieu goc tra ve tu dich vu tra cuu MST.';
comment on column public.customers.odoo_partner_id is
    'ID contact/partner tu Odoo de doi chieu dong bo.';
comment on column public.customers.misa_customer_id is
    'ID khach hang tu MISA de doi chieu cong no.';

-- Tim theo ten khach hang.
create index if not exists customers_display_name_lower_idx
    on public.customers (lower(display_name));

-- Tim nhanh theo SDT lien he chinh.
create index if not exists customers_primary_phone_idx
    on public.customers (primary_phone)
    where primary_phone is not null;

-- Loc danh sach khach theo trang thai va nguon.
create index if not exists customers_status_source_idx
    on public.customers (status, source);

-- Phuc vu job kiem tra cac MST chua duoc xac minh.
create index if not exists customers_unverified_tax_idx
    on public.customers (id)
    where tax_code is not null
      and tax_verification_status <> 'verified';

-- Tu dong cap nhat updated_at.
create or replace function public.set_customers_updated_at()
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

-- Chi tao trigger neu chua ton tai; khong drop object cu.
do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'customers_set_updated_at'
          and tgrelid = 'public.customers'::regclass
          and not tgisinternal
    ) then
        create trigger customers_set_updated_at
        before update on public.customers
        for each row
        execute function public.set_customers_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.customers enable row level security;

revoke all on table public.customers from anon, authenticated;
grant select, insert, update on table public.customers to service_role;
grant usage, select on sequence public.customers_id_seq to service_role;

revoke all on function public.set_customers_updated_at() from public;
grant execute on function public.set_customers_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'customers'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.customers (
--     customer_code,
--     customer_type,
--     display_name,
--     legal_name,
--     tax_code,
--     legal_representative,
--     registered_address,
--     primary_contact_name,
--     primary_phone,
--     primary_email,
--     source,
--     tax_verification_status,
--     tax_verified_at,
--     tax_lookup_source
-- ) values (
--     'KH-000001',
--     'business',
--     'Cong ty Dai Phat',
--     'CONG TY TNHH THUONG MAI DAI PHAT',
--     '0123456789',
--     'Nguyen Van A',
--     'TP. Ho Chi Minh',
--     'Tran Thi B',
--     '0901234567',
--     'ketoan@example.com',
--     'web',
--     'verified',
--     now(),
--     'approved_tax_lookup_service'
-- );

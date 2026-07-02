-- Phuc Thanh Audio Group
-- Bang thu tu: quotations + quotation_items
-- Chay sau supabase-customers.sql, supabase-products.sql va supabase-leads.sql.
-- Muc tieu: quan ly bao gia va cac dong san pham trong bao gia.

begin;

create table if not exists public.quotations (
    id bigint generated always as identity primary key,

    -- Ma nghiep vu do Spring Boot sinh, vi du: BG-000001
    quotation_code text not null,

    customer_id bigint not null references public.customers (id),
    lead_id bigint references public.leads (id),

    quotation_date date not null default current_date,
    valid_until date,

    -- Tong tien hang truoc giam gia va thue; backend tinh tu quotation_items.
    subtotal numeric(18, 2) not null default 0,
    discount_amount numeric(18, 2) not null default 0,
    tax_amount numeric(18, 2) not null default 0,
    total_amount numeric(18, 2) generated always as (
        subtotal - discount_amount + tax_amount
    ) stored,

    currency text not null default 'VND',
    status text not null default 'draft',

    -- Link file PDF/Google Drive sau khi xuat bao gia.
    pdf_url text,
    google_doc_url text,

    payment_terms text,
    delivery_terms text,
    warranty_terms text,
    notes text,

    created_by_user_id uuid,
    approved_at timestamptz,
    rejected_at timestamptz,
    sent_at timestamptz,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint quotations_quotation_code_unique unique (quotation_code),
    constraint quotations_quotation_code_format_check
        check (
            btrim(quotation_code) <> ''
            and quotation_code = upper(btrim(quotation_code))
        ),
    constraint quotations_dates_check
        check (valid_until is null or valid_until >= quotation_date),
    constraint quotations_money_non_negative_check
        check (
            subtotal >= 0
            and discount_amount >= 0
            and tax_amount >= 0
            and discount_amount <= subtotal
        ),
    constraint quotations_currency_check
        check (currency in ('VND', 'USD')),
    constraint quotations_status_check
        check (status in ('draft', 'sent', 'approved', 'rejected', 'expired')),
    constraint quotations_status_dates_check
        check (
            (status = 'approved' or approved_at is null)
            and (status = 'rejected' or rejected_at is null)
            and (status in ('sent', 'approved', 'rejected', 'expired') or sent_at is null)
        ),
    constraint quotations_version_non_negative_check
        check (version >= 0)
);

comment on table public.quotations is
    'Phan dau bao gia gui khach hang; danh sach san pham nam trong quotation_items.';
comment on column public.quotations.customer_id is
    'Khach hang nhan bao gia. Bat buoc co customer truoc khi lap bao gia.';
comment on column public.quotations.lead_id is
    'Co hoi ban hang tao ra bao gia; nullable de cho phep bao gia truc tiep.';
comment on column public.quotations.total_amount is
    'Tu dong tinh bang subtotal - discount_amount + tax_amount.';
comment on column public.quotations.created_by_user_id is
    'UUID nhan vien tao bao gia; FK duoc bo sung trong supabase-staff-members.sql.';

-- Tim lich su bao gia theo khach.
create index if not exists quotations_customer_id_idx
    on public.quotations (customer_id);

-- Tim bao gia theo lead/pipeline.
create index if not exists quotations_lead_id_idx
    on public.quotations (lead_id)
    where lead_id is not null;

-- Loc danh sach bao gia theo trang thai va ngay bao gia.
create index if not exists quotations_status_date_idx
    on public.quotations (status, quotation_date desc);

-- Job xu ly bao gia het han.
create index if not exists quotations_valid_until_idx
    on public.quotations (valid_until)
    where status in ('draft', 'sent')
      and valid_until is not null;

create table if not exists public.quotation_items (
    id bigint generated always as identity primary key,
    quotation_id bigint not null references public.quotations (id) on delete cascade,

    -- Nullable de bao gia hang/dich vu chua co trong danh muc products.
    product_id bigint references public.products (id),

    product_sku text,
    product_name text not null,
    unit text not null default 'piece',
    quantity numeric(14, 3) not null default 1,
    unit_price numeric(18, 2) not null default 0,
    cost_price numeric(18, 2) not null default 0,
    discount_amount numeric(18, 2) not null default 0,
    tax_rate numeric(5, 2) not null default 0,
    line_subtotal numeric(18, 2) generated always as (
        quantity * unit_price - discount_amount
    ) stored,
    tax_amount numeric(18, 2) generated always as (
        (quantity * unit_price - discount_amount) * tax_rate / 100
    ) stored,
    line_total numeric(18, 2) generated always as (
        (quantity * unit_price - discount_amount)
        + ((quantity * unit_price - discount_amount) * tax_rate / 100)
    ) stored,
    notes text,
    sort_order smallint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint quotation_items_product_name_not_blank_check
        check (btrim(product_name) <> ''),
    constraint quotation_items_unit_check
        check (unit in ('piece', 'set', 'box', 'meter')),
    constraint quotation_items_quantity_positive_check
        check (quantity > 0),
    constraint quotation_items_money_non_negative_check
        check (
            unit_price >= 0
            and cost_price >= 0
            and discount_amount >= 0
        ),
    constraint quotation_items_discount_not_over_line_check
        check (discount_amount <= quantity * unit_price),
    constraint quotation_items_tax_rate_check
        check (tax_rate between 0 and 100),
    constraint quotation_items_sort_order_non_negative_check
        check (sort_order >= 0)
);

comment on table public.quotation_items is
    'Cac dong san pham/dich vu trong bao gia, luu snapshot ten/gia tai thoi diem bao gia.';
comment on column public.quotation_items.product_id is
    'Nullable vi co the bao gia hang/dich vu chua co trong danh muc products.';
comment on column public.quotation_items.cost_price is
    'Snapshot gia von tai thoi diem bao gia; chi backend/bao cao noi bo duoc dung.';
comment on column public.quotation_items.line_total is
    'Tu dong tinh tong dong sau giam gia va thue.';

create index if not exists quotation_items_quotation_sort_order_idx
    on public.quotation_items (quotation_id, sort_order);

create index if not exists quotation_items_product_id_idx
    on public.quotation_items (product_id)
    where product_id is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_quotations_updated_at()
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

create or replace function public.set_quotation_items_updated_at()
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
        where tgname = 'quotations_set_updated_at'
          and tgrelid = 'public.quotations'::regclass
          and not tgisinternal
    ) then
        create trigger quotations_set_updated_at
        before update on public.quotations
        for each row
        execute function public.set_quotations_updated_at();
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'quotation_items_set_updated_at'
          and tgrelid = 'public.quotation_items'::regclass
          and not tgisinternal
    ) then
        create trigger quotation_items_set_updated_at
        before update on public.quotation_items
        for each row
        execute function public.set_quotation_items_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.quotations enable row level security;
alter table public.quotation_items enable row level security;

revoke all on table public.quotations from anon, authenticated;
revoke all on table public.quotation_items from anon, authenticated;

grant select, insert, update on table public.quotations to service_role;
grant select, insert, update on table public.quotation_items to service_role;
grant usage, select on sequence public.quotations_id_seq to service_role;
grant usage, select on sequence public.quotation_items_id_seq to service_role;

revoke all on function public.set_quotations_updated_at() from public;
revoke all on function public.set_quotation_items_updated_at() from public;
grant execute on function public.set_quotations_updated_at() to service_role;
grant execute on function public.set_quotation_items_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name in ('quotations', 'quotation_items')
order by table_name, ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.quotations (
--     quotation_code,
--     customer_id,
--     lead_id,
--     quotation_date,
--     valid_until,
--     subtotal,
--     discount_amount,
--     tax_amount,
--     status,
--     payment_terms,
--     delivery_terms,
--     warranty_terms
-- ) values (
--     'BG-000001',
--     1,
--     1,
--     current_date,
--     current_date + 15,
--     15000000,
--     0,
--     1500000,
--     'draft',
--     'Thanh toan theo thoa thuan',
--     'Giao hang theo lich hai ben xac nhan',
--     'Bao hanh theo chinh sach hang'
-- );
--
-- insert into public.quotation_items (
--     quotation_id,
--     product_id,
--     product_sku,
--     product_name,
--     unit,
--     quantity,
--     unit_price,
--     cost_price,
--     tax_rate,
--     sort_order
-- ) values (
--     1,
--     1,
--     'SP-000001',
--     'Loa JBL Control 28-1',
--     'piece',
--     1,
--     15000000,
--     12000000,
--     10,
--     1
-- );

-- Phuc Thanh Audio Group
-- Bang dau tien: products
-- Chay toan bo file nay trong Supabase Dashboard > SQL Editor.

begin;

create table if not exists public.products (
    id bigint generated always as identity primary key,

    -- Ma nghiep vu de hien thi/tim kiem, vi du: SP-000001
    sku text not null,
    name text not null,
    category text not null,
    brand text,
    model text,

    -- VND: dung numeric, khong dung float/double
    cost_price numeric(18, 2) not null default 0,
    sale_price numeric(18, 2) not null default 0,

    -- So luong co 3 chu so thap phan de ho tro don vi "met"
    unit text not null default 'piece',
    stock_quantity numeric(14, 3) not null default 0,
    minimum_stock numeric(14, 3) not null default 0,

    default_warranty_months smallint not null default 12,
    description text,
    specifications jsonb not null default '{}'::jsonb,
    image_url text,

    -- Trang thai kinh doanh, khong dung status='out_of_stock'
    -- vi het hang da duoc suy ra tu stock_quantity.
    status text not null default 'active',
    stock_status text generated always as (
        case
            when status <> 'active' then 'unavailable'
            when stock_quantity = 0 then 'out_of_stock'
            when stock_quantity <= minimum_stock then 'low_stock'
            else 'in_stock'
        end
    ) stored,

    -- Dung cho optimistic locking ben Spring Boot/JPA.
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint products_sku_unique unique (sku),
    constraint products_sku_format_check
        check (btrim(sku) <> '' and sku = upper(btrim(sku))),
    constraint products_name_not_blank_check
        check (btrim(name) <> ''),
    constraint products_category_check
        check (category in ('audio', 'tv', 'led', 'av', 'elv', 'ict', 'accessory')),
    constraint products_unit_check
        check (unit in ('piece', 'set', 'box', 'meter')),
    constraint products_prices_non_negative_check
        check (cost_price >= 0 and sale_price >= 0),
    constraint products_stock_non_negative_check
        check (stock_quantity >= 0 and minimum_stock >= 0),
    constraint products_warranty_months_check
        check (default_warranty_months between 0 and 240),
    constraint products_specifications_object_check
        check (jsonb_typeof(specifications) = 'object'),
    constraint products_status_check
        check (status in ('active', 'inactive', 'discontinued')),
    constraint products_version_non_negative_check
        check (version >= 0)
);

comment on table public.products is
    'Danh muc san pham, gia va ton kho hien tai cua Phuc Thanh Audio Group.';
comment on column public.products.sku is
    'Ma san pham nghiep vu, viet hoa, vi du SP-000001.';
comment on column public.products.category is
    'audio, tv, led, av, elv, ict hoac accessory.';
comment on column public.products.stock_status is
    'Tu dong suy ra: unavailable, out_of_stock, low_stock hoac in_stock.';
comment on column public.products.specifications is
    'Thong so ky thuat linh hoat theo tung nhom san pham.';

-- Tim kiem/loc danh muc san pham dang kinh doanh.
create index if not exists products_active_category_name_idx
    on public.products (category, name)
    where status = 'active';

create index if not exists products_active_brand_idx
    on public.products (brand)
    where status = 'active' and brand is not null;

create index if not exists products_name_lower_idx
    on public.products (lower(name));

-- Toi uu job canh bao ton kho.
create index if not exists products_low_stock_idx
    on public.products (id)
    where status = 'active' and stock_quantity <= minimum_stock;

-- Tu dong cap nhat updated_at.
-- Dung ten rieng cho Products de khong de len function cua bang khac.
create or replace function public.set_products_updated_at()
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
        where tgname = 'products_set_updated_at'
          and tgrelid = 'public.products'::regclass
          and not tgisinternal
    ) then
        create trigger products_set_updated_at
        before update on public.products
        for each row
        execute function public.set_products_updated_at();
    end if;
end;
$$;

-- public la schema co the duoc expose qua Supabase Data API.
-- Bat RLS nhung khong mo policy cho anon/authenticated:
-- React phai goi Spring Boot, khong truy cap bang truc tiep.
alter table public.products enable row level security;

revoke all on table public.products from anon, authenticated;
-- Khong grant DELETE: san pham ngung kinh doanh bang status='discontinued'.
grant select, insert, update on table public.products to service_role;
grant usage, select on sequence public.products_id_seq to service_role;

revoke all on function public.set_products_updated_at() from public;
grant execute on function public.set_products_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'products'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.products (
--     sku, name, category, brand, model,
--     cost_price, sale_price, unit,
--     stock_quantity, minimum_stock,
--     default_warranty_months, description, specifications
-- ) values (
--     'SP-000001',
--     'Loa JBL Control 28-1',
--     'audio',
--     'JBL',
--     'Control 28-1',
--     12000000,
--     15000000,
--     'piece',
--     7,
--     2,
--     12,
--     'Loa lap dat trong nha/ngoai troi',
--     '{"power_w": 240, "impedance_ohm": 8}'::jsonb
-- );

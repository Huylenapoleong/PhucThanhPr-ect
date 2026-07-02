-- Phuc Thanh Audio Group
-- Bang thu ba: leads + lead_items
-- Chay sau supabase-customers.sql va supabase-products.sql.
-- Muc tieu: quan ly pipeline ban hang va gio hang checkout bi bo do.

begin;

create table if not exists public.leads (
    id bigint generated always as identity primary key,

    -- Ma nghiep vu do Spring Boot sinh, vi du: LD-000001
    lead_code text not null,

    -- Nullable de van ghi nhan duoc lead tu FB/Zalo/Call khi chua co ho so KH.
    customer_id bigint references public.customers (id),

    name text not null,
    source text not null default 'manual',

    -- Snapshot lien he tai thoi diem phat sinh lead.
    contact_name text,
    contact_phone text,
    contact_email text,
    company_name text,
    tax_code text,

    project_url text,
    requirement text,

    score smallint not null default 0,
    temperature text not null default 'cold',
    stage text not null default 'new',

    estimated_value numeric(18, 2) not null default 0,
    expected_close_date date,
    assigned_to_user_id uuid,

    last_activity_at timestamptz,
    next_follow_up_at timestamptz,
    closed_at timestamptz,
    lost_reason text,

    -- Lien ket nguon ngoai: cart/session/call/message/tender.
    origin_ref_type text not null default 'manual',
    origin_ref_id text,
    origin_payload jsonb not null default '{}'::jsonb,

    notes text,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint leads_lead_code_unique unique (lead_code),
    constraint leads_lead_code_format_check
        check (
            btrim(lead_code) <> ''
            and lead_code = upper(btrim(lead_code))
        ),
    constraint leads_name_not_blank_check
        check (btrim(name) <> ''),
    constraint leads_source_check
        check (
            source in (
                'web',
                'web_cart',
                'facebook',
                'zalo',
                'call',
                'dauthau',
                'referral',
                'manual',
                'other'
            )
        ),
    constraint leads_contact_phone_not_blank_check
        check (
            contact_phone is null
            or (btrim(contact_phone) <> '' and contact_phone = btrim(contact_phone))
        ),
    constraint leads_contact_email_not_blank_check
        check (
            contact_email is null
            or (btrim(contact_email) <> '' and contact_email = btrim(contact_email))
        ),
    constraint leads_tax_code_not_blank_check
        check (
            tax_code is null
            or (btrim(tax_code) <> '' and tax_code = btrim(tax_code))
        ),
    constraint leads_score_range_check
        check (score between 0 and 100),
    constraint leads_temperature_check
        check (temperature in ('hot', 'warm', 'cold')),
    constraint leads_stage_check
        check (
            stage in (
                'new',
                'contacted',
                'consulting',
                'quoted',
                'negotiating',
                'won',
                'delivering',
                'collecting',
                'closed',
                'lost'
            )
        ),
    constraint leads_estimated_value_non_negative_check
        check (estimated_value >= 0),
    constraint leads_origin_ref_type_check
        check (
            origin_ref_type in (
                'cart',
                'call',
                'message',
                'tender',
                'manual',
                'other'
            )
        ),
    constraint leads_origin_ref_id_not_blank_check
        check (
            origin_ref_id is null
            or (btrim(origin_ref_id) <> '' and origin_ref_id = btrim(origin_ref_id))
        ),
    constraint leads_origin_payload_object_check
        check (jsonb_typeof(origin_payload) = 'object'),
    constraint leads_lost_reason_only_when_lost_check
        check (stage = 'lost' or lost_reason is null),
    constraint leads_version_non_negative_check
        check (version >= 0)
);

comment on table public.leads is
    'Co hoi ban hang/pipeline; co the den tu web, gio hang bo do, FB, Zalo, call hoac DauThau.';
comment on column public.leads.customer_id is
    'Nullable vi lead co the xuat hien truoc khi tao ho so customer.';
comment on column public.leads.source is
    'web_cart dung cho khach da dang nhap, them gio hang nhung qua han checkout.';
comment on column public.leads.origin_payload is
    'Snapshot JSON tu gio hang, cuoc goi, tin nhan, DauThau hoac he thong ngoai.';
comment on column public.leads.assigned_to_user_id is
    'UUID nhan vien phu trach; FK duoc bo sung trong supabase-staff-members.sql.';

-- Khong tao trung lead neu cung mot nguon ngoai, vi du cung cart_id.
create unique index if not exists leads_origin_ref_unique_idx
    on public.leads (origin_ref_type, origin_ref_id)
    where origin_ref_id is not null;

-- Tim pipeline theo khach hang.
create index if not exists leads_customer_id_idx
    on public.leads (customer_id)
    where customer_id is not null;

-- Man hinh Kanban: loc theo stage va sap xep lead moi len truoc.
create index if not exists leads_stage_created_at_idx
    on public.leads (stage, created_at desc);

-- Job nhac sales follow-up cac lead dang mo.
create index if not exists leads_open_follow_up_idx
    on public.leads (next_follow_up_at)
    where stage not in ('closed', 'lost')
      and next_follow_up_at is not null;

-- Loc lead theo nguon va nhiet do.
create index if not exists leads_source_temperature_idx
    on public.leads (source, temperature);

create table if not exists public.lead_items (
    id bigint generated always as identity primary key,
    lead_id bigint not null references public.leads (id) on delete cascade,

    -- Nullable de nhap nhu cau tu tin nhan/call khi chua match duoc product.
    product_id bigint references public.products (id),

    product_sku text,
    product_name text not null,
    unit text not null default 'piece',
    quantity numeric(14, 3) not null default 1,
    unit_price numeric(18, 2) not null default 0,
    discount_amount numeric(18, 2) not null default 0,
    line_total numeric(18, 2) generated always as (
        (quantity * unit_price) - discount_amount
    ) stored,
    notes text,
    sort_order smallint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint lead_items_product_name_not_blank_check
        check (btrim(product_name) <> ''),
    constraint lead_items_unit_check
        check (unit in ('piece', 'set', 'box', 'meter')),
    constraint lead_items_quantity_positive_check
        check (quantity > 0),
    constraint lead_items_money_non_negative_check
        check (unit_price >= 0 and discount_amount >= 0),
    constraint lead_items_discount_not_over_line_check
        check (discount_amount <= quantity * unit_price),
    constraint lead_items_sort_order_non_negative_check
        check (sort_order >= 0)
);

comment on table public.lead_items is
    'Snapshot san pham/nhu cau trong lead, bao gom gio hang bi bo do.';
comment on column public.lead_items.product_id is
    'Nullable vi khach co the goi/nhan tin ten san pham chua match voi products.';
comment on column public.lead_items.line_total is
    'Tu dong tinh tu quantity, unit_price va discount_amount.';

create index if not exists lead_items_lead_id_idx
    on public.lead_items (lead_id);

create index if not exists lead_items_product_id_idx
    on public.lead_items (product_id)
    where product_id is not null;

create index if not exists lead_items_lead_sort_order_idx
    on public.lead_items (lead_id, sort_order);

-- Tu dong cap nhat updated_at.
create or replace function public.set_leads_updated_at()
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

create or replace function public.set_lead_items_updated_at()
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
        where tgname = 'leads_set_updated_at'
          and tgrelid = 'public.leads'::regclass
          and not tgisinternal
    ) then
        create trigger leads_set_updated_at
        before update on public.leads
        for each row
        execute function public.set_leads_updated_at();
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'lead_items_set_updated_at'
          and tgrelid = 'public.lead_items'::regclass
          and not tgisinternal
    ) then
        create trigger lead_items_set_updated_at
        before update on public.lead_items
        for each row
        execute function public.set_lead_items_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.leads enable row level security;
alter table public.lead_items enable row level security;

revoke all on table public.leads from anon, authenticated;
revoke all on table public.lead_items from anon, authenticated;

grant select, insert, update on table public.leads to service_role;
grant select, insert, update on table public.lead_items to service_role;
grant usage, select on sequence public.leads_id_seq to service_role;
grant usage, select on sequence public.lead_items_id_seq to service_role;

revoke all on function public.set_leads_updated_at() from public;
revoke all on function public.set_lead_items_updated_at() from public;
grant execute on function public.set_leads_updated_at() to service_role;
grant execute on function public.set_lead_items_updated_at() to service_role;

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
  and table_name in ('leads', 'lead_items')
order by table_name, ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.leads (
--     lead_code,
--     customer_id,
--     name,
--     source,
--     contact_name,
--     contact_phone,
--     requirement,
--     score,
--     temperature,
--     stage,
--     estimated_value,
--     origin_ref_type,
--     origin_ref_id,
--     origin_payload
-- ) values (
--     'LD-000001',
--     1,
--     'Gio hang bo do - Loa hoi truong',
--     'web_cart',
--     'Nguyen Van A',
--     '0901234567',
--     'Khach da them loa vao gio hang nhung chua checkout',
--     65,
--     'warm',
--     'new',
--     15000000,
--     'cart',
--     'cart_abc_123',
--     '{"checkout_timeout_minutes": 60}'::jsonb
-- );
--
-- insert into public.lead_items (
--     lead_id,
--     product_id,
--     product_sku,
--     product_name,
--     unit,
--     quantity,
--     unit_price,
--     sort_order
-- ) values (
--     1,
--     1,
--     'SP-000001',
--     'Loa JBL Control 28-1',
--     'piece',
--     1,
--     15000000,
--     1
-- );

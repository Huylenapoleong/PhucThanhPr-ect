-- Phuc Thanh Audio Group
-- Bang thu muoi ba: invoices + invoice_items
-- Chay sau supabase-kpi-snapshots.sql (va sau supabase-staff-members.sql).
-- Muc tieu: luu hoa don dien tu, snapshot nguoi mua va cac dong hang.
-- MISA/nha cung cap hoa don la noi phat hanh hoa don thue chinh thuc;
-- bang nay luu quy trinh, ket qua phat hanh va du lieu doi soat.

begin;

create table if not exists public.invoices (
    id bigint generated always as identity primary key,

    -- Ma noi bo do Spring Boot sinh, vi du: INV-000001.
    invoice_code text not null,

    customer_id bigint not null references public.customers (id),

    -- Ban le co the khong co hop dong/bao gia, vi vay hai FK nay nullable.
    contract_id bigint references public.contracts (id),
    quotation_id bigint references public.quotations (id),

    -- Hoa don dieu chinh/thay the phai tro ve hoa don goc.
    original_invoice_id bigint references public.invoices (id),
    invoice_type text not null default 'sale',

    -- Snapshot nguoi mua tai thoi diem lap hoa don.
    buyer_name text not null,
    buyer_tax_code text,
    buyer_address text,
    buyer_email text,

    issue_date date,
    due_date date,
    currency text not null default 'VND',

    -- Backend tong hop tu invoice_items hoac dong bo gia tri chinh thuc tu MISA.
    -- Hoa don adjustment co the mang gia tri am khi dieu chinh giam.
    subtotal numeric(18, 2) not null default 0,
    discount_amount numeric(18, 2) not null default 0,
    tax_amount numeric(18, 2) not null default 0,
    total_amount numeric(18, 2) generated always as (
        subtotal - discount_amount + tax_amount
    ) stored,

    -- Tong da thu la gia tri doc nhanh; payment_records moi la so giao dich goc.
    paid_amount numeric(18, 2) not null default 0,
    remaining_amount numeric(18, 2) generated always as (
        (subtotal - discount_amount + tax_amount) - paid_amount
    ) stored,

    invoice_status text not null default 'draft',
    payment_status text not null default 'unpaid',

    -- Thong tin do nha cung cap hoa don dien tu/MISA tra ve.
    provider text,
    external_invoice_id text,
    invoice_series text,
    invoice_number text,
    tax_authority_code text,
    lookup_code text,
    xml_url text,
    pdf_url text,
    lookup_url text,
    provider_payload jsonb not null default '{}'::jsonb,

    created_by_user_id uuid references public.staff_members (id)
        on update cascade on delete set null,
    issued_by_user_id uuid references public.staff_members (id)
        on update cascade on delete set null,

    issued_at timestamptz,
    sent_at timestamptz,
    cancelled_at timestamptz,
    issue_error text,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint invoices_invoice_code_unique unique (invoice_code),
    constraint invoices_invoice_code_format_check
        check (
            btrim(invoice_code) <> ''
            and invoice_code = upper(btrim(invoice_code))
        ),
    constraint invoices_type_check
        check (invoice_type in ('sale', 'adjustment', 'replacement')),
    constraint invoices_original_invoice_check
        check (
            (
                invoice_type = 'sale'
                and original_invoice_id is null
            )
            or (
                invoice_type in ('adjustment', 'replacement')
                and original_invoice_id is not null
            )
        ),
    constraint invoices_not_self_reference_check
        check (original_invoice_id is null or original_invoice_id <> id),
    constraint invoices_buyer_name_not_blank_check
        check (btrim(buyer_name) <> ''),
    constraint invoices_buyer_tax_code_not_blank_check
        check (
            buyer_tax_code is null
            or (
                btrim(buyer_tax_code) <> ''
                and buyer_tax_code = btrim(buyer_tax_code)
            )
        ),
    constraint invoices_buyer_email_not_blank_check
        check (
            buyer_email is null
            or (
                btrim(buyer_email) <> ''
                and buyer_email = lower(btrim(buyer_email))
            )
        ),
    constraint invoices_dates_check
        check (
            issue_date is null
            or due_date is null
            or due_date >= issue_date
        ),
    constraint invoices_currency_check
        check (currency in ('VND', 'USD')),
    constraint invoices_sale_money_check
        check (
            invoice_type = 'adjustment'
            or (
                subtotal >= 0
                and discount_amount >= 0
                and tax_amount >= 0
                and discount_amount <= subtotal
            )
        ),
    constraint invoices_paid_amount_check
        check (
            paid_amount >= 0
            and (
                (subtotal - discount_amount + tax_amount) < 0
                or paid_amount <= (subtotal - discount_amount + tax_amount)
            )
        ),
    constraint invoices_status_check
        check (
            invoice_status in (
                'draft',
                'pending_issue',
                'issued',
                'replaced',
                'cancelled',
                'issue_failed'
            )
        ),
    constraint invoices_payment_status_check
        check (payment_status in ('unpaid', 'partial', 'paid', 'overdue')),
    constraint invoices_issued_at_check
        check (
            invoice_status in ('issued', 'replaced', 'cancelled')
            or issued_at is null
        ),
    constraint invoices_cancelled_at_check
        check (invoice_status = 'cancelled' or cancelled_at is null),
    constraint invoices_provider_fields_check
        check (
            (external_invoice_id is null and provider is null)
            or (
                external_invoice_id is null
                and provider is not null
                and btrim(provider) <> ''
            )
            or (
                external_invoice_id is not null
                and btrim(external_invoice_id) <> ''
                and provider is not null
                and btrim(provider) <> ''
            )
        ),
    constraint invoices_provider_payload_object_check
        check (jsonb_typeof(provider_payload) = 'object'),
    constraint invoices_version_non_negative_check
        check (version >= 0)
);

comment on table public.invoices is
    'Hoa don dien tu va ket qua phat hanh tu MISA/nha cung cap; khong thay the he thong hoa don thue.';
comment on column public.invoices.invoice_code is
    'Ma noi bo cua he thong; invoice_number la so hoa don chinh thuc do provider tra ve.';
comment on column public.invoices.contract_id is
    'Nullable vi ban le co the phat hanh hoa don ma khong lap hop dong.';
comment on column public.invoices.original_invoice_id is
    'Hoa don goc khi invoice_type la adjustment hoac replacement.';
comment on column public.invoices.buyer_name is
    'Snapshot ten nguoi mua tai thoi diem lap; khong doc dong tu customers khi xem lich su.';
comment on column public.invoices.paid_amount is
    'Gia tri tong hop do backend cap nhat tu payment_records confirmed.';
comment on column public.invoices.provider_payload is
    'Payload JSON goc cua MISA/nha cung cap de doi soat va xu ly su co.';

-- Lich su hoa don theo khach hang.
create index if not exists invoices_customer_issue_date_idx
    on public.invoices (customer_id, issue_date desc);

-- FK indexes.
create index if not exists invoices_contract_id_idx
    on public.invoices (contract_id)
    where contract_id is not null;

create index if not exists invoices_quotation_id_idx
    on public.invoices (quotation_id)
    where quotation_id is not null;

create index if not exists invoices_original_invoice_id_idx
    on public.invoices (original_invoice_id)
    where original_invoice_id is not null;

create index if not exists invoices_created_by_user_id_idx
    on public.invoices (created_by_user_id)
    where created_by_user_id is not null;

create index if not exists invoices_issued_by_user_id_idx
    on public.invoices (issued_by_user_id)
    where issued_by_user_id is not null;

-- Hang doi phat hanh/thu lai hoa don.
create index if not exists invoices_pending_issue_idx
    on public.invoices (created_at)
    where invoice_status in ('pending_issue', 'issue_failed');

-- Job nhac cong no hoa don.
create index if not exists invoices_open_due_date_idx
    on public.invoices (due_date, customer_id)
    where invoice_status = 'issued'
      and payment_status in ('unpaid', 'partial', 'overdue')
      and due_date is not null;

-- Chong trung callback/provider invoice.
create unique index if not exists invoices_provider_external_id_unique_idx
    on public.invoices (provider, external_invoice_id)
    where provider is not null
      and external_invoice_id is not null;

create unique index if not exists invoices_provider_official_number_unique_idx
    on public.invoices (provider, invoice_series, invoice_number)
    where provider is not null
      and invoice_series is not null
      and invoice_number is not null;

-- Gan lich nhac cong no truc tiep voi hoa don, ke ca hoa don ban le khong co hop dong.
alter table public.customer_reminders
    add column if not exists invoice_id bigint;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'customer_reminders_invoice_id_fkey'
          and conrelid = 'public.customer_reminders'::regclass
    ) then
        alter table public.customer_reminders
        add constraint customer_reminders_invoice_id_fkey
        foreign key (invoice_id)
        references public.invoices (id)
        on delete set null;
    end if;
end;
$$;

create index if not exists customer_reminders_invoice_id_idx
    on public.customer_reminders (invoice_id)
    where invoice_id is not null;

comment on column public.customer_reminders.invoice_id is
    'Hoa don lien quan toi lich nhac thanh toan/cong no; nullable cho reminder loai khac.';

create table if not exists public.invoice_items (
    id bigint generated always as identity primary key,
    invoice_id bigint not null references public.invoices (id) on delete cascade,

    product_id bigint references public.products (id),
    quotation_item_id bigint references public.quotation_items (id),

    -- Snapshot dong hang tai thoi diem phat hanh.
    product_sku text,
    product_name text not null,
    unit text not null default 'piece',
    item_type text not null default 'sale',
    quantity numeric(14, 3) not null default 1,
    unit_price numeric(18, 2) not null default 0,
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

    constraint invoice_items_product_name_not_blank_check
        check (btrim(product_name) <> ''),
    constraint invoice_items_unit_check
        check (unit in ('piece', 'set', 'box', 'meter', 'service', 'other')),
    constraint invoice_items_type_check
        check (item_type in ('sale', 'adjustment', 'replacement')),
    constraint invoice_items_quantity_positive_check
        check (quantity > 0),
    constraint invoice_items_sale_money_check
        check (
            item_type = 'adjustment'
            or (
                unit_price >= 0
                and discount_amount >= 0
                and discount_amount <= quantity * unit_price
            )
        ),
    constraint invoice_items_tax_rate_check
        check (tax_rate between 0 and 100),
    constraint invoice_items_sort_order_non_negative_check
        check (sort_order >= 0)
);

comment on table public.invoice_items is
    'Snapshot cac dong hang/dich vu tren hoa don; khong doc lai gia hien tai cua products.';
comment on column public.invoice_items.quotation_item_id is
    'Dong bao gia nguon; nullable cho hoa don ban le/dieu chinh.';
comment on column public.invoice_items.item_type is
    'Adjustment cho phep gia tri am de mirror hoa don dieu chinh giam tu provider.';

create index if not exists invoice_items_invoice_sort_order_idx
    on public.invoice_items (invoice_id, sort_order);

create index if not exists invoice_items_product_id_idx
    on public.invoice_items (product_id)
    where product_id is not null;

create index if not exists invoice_items_quotation_item_id_idx
    on public.invoice_items (quotation_item_id)
    where quotation_item_id is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_invoices_updated_at()
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

create or replace function public.set_invoice_items_updated_at()
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
        where tgname = 'invoices_set_updated_at'
          and tgrelid = 'public.invoices'::regclass
          and not tgisinternal
    ) then
        create trigger invoices_set_updated_at
        before update on public.invoices
        for each row
        execute function public.set_invoices_updated_at();
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'invoice_items_set_updated_at'
          and tgrelid = 'public.invoice_items'::regclass
          and not tgisinternal
    ) then
        create trigger invoice_items_set_updated_at
        before update on public.invoice_items
        for each row
        execute function public.set_invoice_items_updated_at();
    end if;
end;
$$;

-- Frontend goi backend; khong cho browser doc/ghi truc tiep.
alter table public.invoices enable row level security;
alter table public.invoice_items enable row level security;

revoke all on table public.invoices from anon, authenticated;
revoke all on table public.invoice_items from anon, authenticated;

grant select, insert, update on table public.invoices to service_role;
grant select, insert, update on table public.invoice_items to service_role;
grant usage, select on sequence public.invoices_id_seq to service_role;
grant usage, select on sequence public.invoice_items_id_seq to service_role;

revoke all on function public.set_invoices_updated_at() from public;
revoke all on function public.set_invoice_items_updated_at() from public;
grant execute on function public.set_invoices_updated_at() to service_role;
grant execute on function public.set_invoice_items_updated_at() to service_role;

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
  and table_name in ('invoices', 'invoice_items')
order by table_name, ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.invoices (
--     invoice_code,
--     customer_id,
--     contract_id,
--     quotation_id,
--     buyer_name,
--     buyer_tax_code,
--     buyer_address,
--     issue_date,
--     due_date,
--     subtotal,
--     tax_amount,
--     invoice_status,
--     payment_status
-- )
-- values (
--     'INV-000001',
--     1,
--     1,
--     1,
--     'CONG TY TNHH KHACH HANG',
--     '0312345678',
--     'TP HCM',
--     current_date,
--     current_date + 30,
--     10000000,
--     1000000,
--     'draft',
--     'unpaid'
-- )
-- returning id, invoice_code, total_amount, remaining_amount;

-- Phuc Thanh Audio Group
-- Bang thu sau: customer_assets + repair_requests
-- Chay sau supabase-contracts.sql.
-- Muc tieu: quan ly thiet bi da ban giao, bao hanh va sua chua.

begin;

create table if not exists public.customer_assets (
    id bigint generated always as identity primary key,

    -- Ma thiet bi/asset do Spring Boot sinh, vi du: TB-000001
    asset_code text not null,

    customer_id bigint not null references public.customers (id),
    product_id bigint not null references public.products (id),
    contract_id bigint references public.contracts (id),

    -- Snapshot san pham tai thoi diem ban giao.
    product_sku text,
    product_name text not null,

    serial_number text,
    purchase_date date,
    delivered_at timestamptz,

    warranty_months smallint not null default 12,
    warranty_starts_on date,
    warranty_expires_on date,

    installation_address text,
    status text not null default 'active',
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint customer_assets_asset_code_unique unique (asset_code),
    constraint customer_assets_serial_number_unique unique (serial_number),
    constraint customer_assets_asset_code_format_check
        check (
            btrim(asset_code) <> ''
            and asset_code = upper(btrim(asset_code))
        ),
    constraint customer_assets_product_name_not_blank_check
        check (btrim(product_name) <> ''),
    constraint customer_assets_serial_number_not_blank_check
        check (
            serial_number is null
            or (btrim(serial_number) <> '' and serial_number = btrim(serial_number))
        ),
    constraint customer_assets_warranty_months_check
        check (warranty_months between 0 and 240),
    constraint customer_assets_warranty_dates_check
        check (
            warranty_starts_on is null
            or warranty_expires_on is null
            or warranty_expires_on >= warranty_starts_on
        ),
    constraint customer_assets_status_check
        check (status in ('active', 'expired', 'returned', 'retired')),
    constraint customer_assets_version_non_negative_check
        check (version >= 0)
);

comment on table public.customer_assets is
    'Thiet bi/serial da ban giao cho khach, dung lam nen cho bao hanh va sua chua.';
comment on column public.customer_assets.customer_id is
    'Khach hang dang so huu thiet bi.';
comment on column public.customer_assets.product_id is
    'San pham goc trong danh muc products.';
comment on column public.customer_assets.contract_id is
    'Hop dong ban giao thiet bi; nullable cho du lieu cu hoac ban giao ngoai hop dong.';
comment on column public.customer_assets.serial_number is
    'Serial thiet bi; unique khi co gia tri.';
comment on column public.customer_assets.warranty_expires_on is
    'Ngay het han bao hanh, backend tinh khi ban giao hoac nhap tay khi import du lieu cu.';

-- Tim tai san/thiet bi theo khach.
create index if not exists customer_assets_customer_id_idx
    on public.customer_assets (customer_id);

-- Tim thiet bi theo san pham.
create index if not exists customer_assets_product_id_idx
    on public.customer_assets (product_id);

-- Tim thiet bi theo hop dong ban giao.
create index if not exists customer_assets_contract_id_idx
    on public.customer_assets (contract_id)
    where contract_id is not null;

-- Job canh bao thiet bi sap het bao hanh.
create index if not exists customer_assets_warranty_expires_idx
    on public.customer_assets (warranty_expires_on)
    where status = 'active'
      and warranty_expires_on is not null;

create table if not exists public.repair_requests (
    id bigint generated always as identity primary key,

    -- Ma phieu BH/SC do Spring Boot sinh, vi du: SC-000001
    repair_code text not null,

    customer_id bigint not null references public.customers (id),
    customer_asset_id bigint references public.customer_assets (id),

    -- Nullable de tiep nhan sua chua khi chua co asset/serial trong he thong.
    product_id bigint references public.products (id),
    reported_product_name text,
    reported_serial_number text,

    request_type text not null default 'warranty',
    request_channel text not null default 'manual',
    priority text not null default 'normal',

    contact_name text,
    contact_phone text,
    contact_email text,

    issue_description text not null,
    intake_notes text,

    warranty_decision text not null default 'pending',
    warranty_reject_reason text,

    technician_user_id uuid,
    received_at timestamptz not null default now(),
    assigned_at timestamptz,
    processing_started_at timestamptz,
    expected_return_at timestamptz,
    completed_at timestamptz,
    returned_at timestamptz,
    cancelled_at timestamptz,

    status text not null default 'received',

    parts_cost numeric(18, 2) not null default 0,
    labor_cost numeric(18, 2) not null default 0,
    outsourced_cost numeric(18, 2) not null default 0,
    total_cost numeric(18, 2) generated always as (
        parts_cost + labor_cost + outsourced_cost
    ) stored,

    resolution text,
    customer_note text,
    internal_notes text,

    notification_channel text not null default 'none',
    notification_status text not null default 'not_required',
    customer_notified_at timestamptz,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint repair_requests_repair_code_unique unique (repair_code),
    constraint repair_requests_repair_code_format_check
        check (
            btrim(repair_code) <> ''
            and repair_code = upper(btrim(repair_code))
        ),
    constraint repair_requests_product_identified_check
        check (
            customer_asset_id is not null
            or product_id is not null
            or (reported_product_name is not null and btrim(reported_product_name) <> '')
        ),
    constraint repair_requests_reported_serial_not_blank_check
        check (
            reported_serial_number is null
            or (
                btrim(reported_serial_number) <> ''
                and reported_serial_number = btrim(reported_serial_number)
            )
        ),
    constraint repair_requests_type_check
        check (request_type in ('warranty', 'paid_repair', 'outsourced')),
    constraint repair_requests_channel_check
        check (request_channel in ('web', 'zalo', 'facebook', 'call', 'manual', 'other')),
    constraint repair_requests_priority_check
        check (priority in ('low', 'normal', 'high', 'urgent')),
    constraint repair_requests_contact_phone_not_blank_check
        check (
            contact_phone is null
            or (btrim(contact_phone) <> '' and contact_phone = btrim(contact_phone))
        ),
    constraint repair_requests_contact_email_not_blank_check
        check (
            contact_email is null
            or (btrim(contact_email) <> '' and contact_email = btrim(contact_email))
        ),
    constraint repair_requests_issue_not_blank_check
        check (btrim(issue_description) <> ''),
    constraint repair_requests_warranty_decision_check
        check (warranty_decision in ('pending', 'approved', 'rejected', 'not_applicable')),
    constraint repair_requests_warranty_reject_reason_check
        check (warranty_decision = 'rejected' or warranty_reject_reason is null),
    constraint repair_requests_status_check
        check (
            status in (
                'received',
                'assigned',
                'processing',
                'waiting_parts',
                'waiting_customer',
                'completed',
                'notified',
                'returned',
                'cancelled'
            )
        ),
    constraint repair_requests_dates_check
        check (
            (assigned_at is null or assigned_at >= received_at)
            and (processing_started_at is null or processing_started_at >= received_at)
            and (expected_return_at is null or expected_return_at >= received_at)
            and (completed_at is null or completed_at >= received_at)
            and (returned_at is null or (completed_at is not null and returned_at >= completed_at))
            and (
                customer_notified_at is null
                or (completed_at is not null and customer_notified_at >= completed_at)
            )
            and (cancelled_at is null or cancelled_at >= received_at)
        ),
    constraint repair_requests_cancelled_at_check
        check (status = 'cancelled' or cancelled_at is null),
    constraint repair_requests_cost_non_negative_check
        check (parts_cost >= 0 and labor_cost >= 0 and outsourced_cost >= 0),
    constraint repair_requests_notification_channel_check
        check (notification_channel in ('none', 'zalo', 'facebook', 'email', 'call', 'telegram')),
    constraint repair_requests_notification_status_check
        check (notification_status in ('not_required', 'pending', 'sent', 'failed')),
    constraint repair_requests_notification_dates_check
        check (
            customer_notified_at is null
            or notification_status = 'sent'
        ),
    constraint repair_requests_version_non_negative_check
        check (version >= 0)
);

comment on table public.repair_requests is
    'Phieu bao hanh/sua chua: tiep nhan yeu cau, KTV xu ly, cap nhat va thong bao khach.';
comment on column public.repair_requests.customer_asset_id is
    'Thiet bi/serial da ban giao; nullable khi khach goi den chua xac dinh duoc asset.';
comment on column public.repair_requests.product_id is
    'San pham lien quan neu chua co customer_asset.';
comment on column public.repair_requests.warranty_decision is
    'Ket luan bao hanh: pending, approved, rejected hoac not_applicable.';
comment on column public.repair_requests.technician_user_id is
    'UUID KTV phu trach; FK duoc bo sung trong supabase-staff-members.sql.';
comment on column public.repair_requests.status is
    'Workflow web: received, assigned, processing, waiting_parts, waiting_customer, completed, notified, returned, cancelled.';
comment on column public.repair_requests.customer_notified_at is
    'Thoi diem thong bao khach da sua xong.';

-- Danh sach phieu theo khach.
create index if not exists repair_requests_customer_id_idx
    on public.repair_requests (customer_id);

-- Lich su sua chua theo thiet bi/serial da ban giao.
create index if not exists repair_requests_customer_asset_id_idx
    on public.repair_requests (customer_asset_id)
    where customer_asset_id is not null;

-- Lich su sua chua theo san pham khi chua co asset.
create index if not exists repair_requests_product_id_idx
    on public.repair_requests (product_id)
    where product_id is not null;

-- Man hinh web cho KTV/Sales loc theo trang thai.
create index if not exists repair_requests_status_received_idx
    on public.repair_requests (status, received_at desc);

-- Hang doi xu ly theo KTV.
create index if not exists repair_requests_technician_status_idx
    on public.repair_requests (technician_user_id, status)
    where technician_user_id is not null;

-- Canh bao tre hen tra hang.
create index if not exists repair_requests_expected_return_idx
    on public.repair_requests (expected_return_at)
    where status not in ('returned', 'cancelled')
      and expected_return_at is not null;

-- Job thong bao khach khi da completed nhung chua notify.
create index if not exists repair_requests_notification_pending_idx
    on public.repair_requests (completed_at)
    where status = 'completed'
      and notification_status in ('pending', 'failed');

-- Tu dong cap nhat updated_at.
create or replace function public.set_customer_assets_updated_at()
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

create or replace function public.set_repair_requests_updated_at()
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
        where tgname = 'customer_assets_set_updated_at'
          and tgrelid = 'public.customer_assets'::regclass
          and not tgisinternal
    ) then
        create trigger customer_assets_set_updated_at
        before update on public.customer_assets
        for each row
        execute function public.set_customer_assets_updated_at();
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'repair_requests_set_updated_at'
          and tgrelid = 'public.repair_requests'::regclass
          and not tgisinternal
    ) then
        create trigger repair_requests_set_updated_at
        before update on public.repair_requests
        for each row
        execute function public.set_repair_requests_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.customer_assets enable row level security;
alter table public.repair_requests enable row level security;

revoke all on table public.customer_assets from anon, authenticated;
revoke all on table public.repair_requests from anon, authenticated;

grant select, insert, update on table public.customer_assets to service_role;
grant select, insert, update on table public.repair_requests to service_role;
grant usage, select on sequence public.customer_assets_id_seq to service_role;
grant usage, select on sequence public.repair_requests_id_seq to service_role;

revoke all on function public.set_customer_assets_updated_at() from public;
revoke all on function public.set_repair_requests_updated_at() from public;
grant execute on function public.set_customer_assets_updated_at() to service_role;
grant execute on function public.set_repair_requests_updated_at() to service_role;

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
  and table_name in ('customer_assets', 'repair_requests')
order by table_name, ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.customer_assets (
--     asset_code,
--     customer_id,
--     product_id,
--     contract_id,
--     product_sku,
--     product_name,
--     serial_number,
--     purchase_date,
--     warranty_months,
--     warranty_starts_on,
--     warranty_expires_on
-- ) values (
--     'TB-000001',
--     1,
--     1,
--     1,
--     'SP-000001',
--     'Loa JBL Control 28-1',
--     'SN-DEMO-0001',
--     current_date,
--     12,
--     current_date,
--     current_date + 365
-- );
--
-- insert into public.repair_requests (
--     repair_code,
--     customer_id,
--     customer_asset_id,
--     request_type,
--     request_channel,
--     priority,
--     contact_name,
--     contact_phone,
--     issue_description,
--     warranty_decision,
--     notification_channel,
--     notification_status
-- ) values (
--     'SC-000001',
--     1,
--     1,
--     'warranty',
--     'zalo',
--     'normal',
--     'Nguyen Van A',
--     '0901234567',
--     'Loa bi re tieng khi mo am luong lon',
--     'pending',
--     'zalo',
--     'pending'
-- );

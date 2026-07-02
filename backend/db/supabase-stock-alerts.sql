-- Phuc Thanh Audio Group
-- Bang thu muoi mot: stock_alerts
-- Chay sau supabase-inventory-movements.sql.
-- Muc tieu: luu lich su canh bao ton kho thap/het hang va trang thai gui thong bao noi bo.

begin;

create table if not exists public.stock_alerts (
    id bigint generated always as identity primary key,

    -- Ma canh bao do Spring Boot sinh, vi du: TK-000001
    alert_code text not null,

    product_id bigint not null references public.products (id),
    inventory_movement_id bigint references public.inventory_movements (id),

    assigned_to_user_id uuid references public.staff_members (id),
    acknowledged_by_user_id uuid references public.staff_members (id),
    resolved_by_user_id uuid references public.staff_members (id),

    warehouse_code text not null default 'MAIN',

    -- Snapshot san pham tai thoi diem canh bao.
    product_sku text,
    product_name text not null,
    unit text not null default 'piece',

    alert_type text not null default 'low_stock',
    severity text not null default 'normal',

    stock_quantity numeric(14, 3) not null,
    minimum_stock numeric(14, 3) not null,
    shortage_quantity numeric(14, 3) generated always as (
        greatest(minimum_stock - stock_quantity, 0)
    ) stored,

    title text not null,
    message text,

    notification_channel text not null default 'telegram',
    notification_target text,

    status text not null default 'open',
    triggered_at timestamptz not null default now(),
    sent_at timestamptz,
    acknowledged_at timestamptz,
    resolved_at timestamptz,
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

    constraint stock_alerts_alert_code_unique unique (alert_code),
    constraint stock_alerts_alert_code_format_check
        check (
            btrim(alert_code) <> ''
            and alert_code = upper(btrim(alert_code))
        ),
    constraint stock_alerts_warehouse_code_format_check
        check (
            btrim(warehouse_code) <> ''
            and warehouse_code = upper(btrim(warehouse_code))
        ),
    constraint stock_alerts_product_sku_not_blank_check
        check (
            product_sku is null
            or (btrim(product_sku) <> '' and product_sku = upper(btrim(product_sku)))
        ),
    constraint stock_alerts_product_name_not_blank_check
        check (btrim(product_name) <> ''),
    constraint stock_alerts_unit_check
        check (unit in ('piece', 'set', 'box', 'meter')),
    constraint stock_alerts_type_check
        check (alert_type in ('low_stock', 'out_of_stock', 'reorder', 'manual')),
    constraint stock_alerts_severity_check
        check (severity in ('low', 'normal', 'high', 'critical')),
    constraint stock_alerts_stock_non_negative_check
        check (stock_quantity >= 0 and minimum_stock >= 0),
    constraint stock_alerts_type_stock_check
        check (
            (alert_type <> 'out_of_stock' or stock_quantity = 0)
            and (
                alert_type not in ('low_stock', 'reorder')
                or stock_quantity <= minimum_stock
            )
        ),
    constraint stock_alerts_title_not_blank_check
        check (btrim(title) <> ''),
    constraint stock_alerts_notification_channel_check
        check (notification_channel in ('telegram', 'zalo', 'email', 'sms', 'manual', 'none')),
    constraint stock_alerts_notification_target_not_blank_check
        check (
            notification_target is null
            or (btrim(notification_target) <> '' and notification_target = btrim(notification_target))
        ),
    constraint stock_alerts_status_check
        check (status in ('open', 'sent', 'acknowledged', 'resolved', 'cancelled', 'failed', 'snoozed')),
    constraint stock_alerts_status_dates_check
        check (
            (status in ('sent', 'acknowledged', 'resolved') or sent_at is null)
            and (status in ('acknowledged', 'resolved') or acknowledged_at is null)
            and (status = 'resolved' or resolved_at is null)
            and (status = 'cancelled' or cancelled_at is null)
            and (status = 'failed' or failed_at is null)
            and (sent_at is null or sent_at >= triggered_at)
            and (acknowledged_at is null or acknowledged_at >= triggered_at)
            and (resolved_at is null or resolved_at >= triggered_at)
            and (cancelled_at is null or cancelled_at >= triggered_at)
            and (failed_at is null or failed_at >= triggered_at)
            and (next_retry_at is null or status in ('open', 'failed', 'snoozed'))
        ),
    constraint stock_alerts_retry_count_non_negative_check
        check (retry_count >= 0),
    constraint stock_alerts_error_only_when_failed_check
        check (status = 'failed' or error_message is null),
    constraint stock_alerts_version_non_negative_check
        check (version >= 0)
);

comment on table public.stock_alerts is
    'Lich su canh bao ton kho thap/het hang va trang thai gui thong bao noi bo.';
comment on column public.stock_alerts.product_id is
    'San pham bi canh bao ton kho.';
comment on column public.stock_alerts.inventory_movement_id is
    'Movement kho lam phat sinh canh bao, neu co.';
comment on column public.stock_alerts.assigned_to_user_id is
    'Nhan vien/bo phan phu trach xu ly canh bao.';
comment on column public.stock_alerts.product_sku is
    'Snapshot SKU tai thoi diem canh bao.';
comment on column public.stock_alerts.product_name is
    'Snapshot ten san pham tai thoi diem canh bao.';
comment on column public.stock_alerts.stock_quantity is
    'Ton hien tai tai thoi diem canh bao.';
comment on column public.stock_alerts.minimum_stock is
    'Nguong ton toi thieu tai thoi diem canh bao.';
comment on column public.stock_alerts.shortage_quantity is
    'So luong thieu so voi ton toi thieu.';
comment on column public.stock_alerts.notification_target is
    'Chat ID/so dien thoai/email/kenh nhan thong bao noi bo.';
comment on column public.stock_alerts.external_message_id is
    'ID tin nhan tu Telegram/Zalo/email/SMS provider neu co.';

-- Khong tao trung canh bao dang mo cho cung san pham, kho va loai canh bao.
create unique index if not exists stock_alerts_active_product_type_unique_idx
    on public.stock_alerts (product_id, warehouse_code, alert_type)
    where status in ('open', 'sent', 'acknowledged', 'failed', 'snoozed')
      and alert_type in ('low_stock', 'out_of_stock', 'reorder');

-- Hang doi canh bao dang can xu ly.
create index if not exists stock_alerts_status_severity_idx
    on public.stock_alerts (status, severity, triggered_at desc);

-- Lich su canh bao theo san pham.
create index if not exists stock_alerts_product_id_idx
    on public.stock_alerts (product_id, triggered_at desc);

-- Canh bao phat sinh tu movement kho.
create index if not exists stock_alerts_inventory_movement_id_idx
    on public.stock_alerts (inventory_movement_id)
    where inventory_movement_id is not null;

-- Canh bao theo nguoi phu trach.
create index if not exists stock_alerts_assigned_to_user_id_idx
    on public.stock_alerts (assigned_to_user_id, status, triggered_at desc)
    where assigned_to_user_id is not null;

-- Canh bao theo nguoi da xac nhan.
create index if not exists stock_alerts_acknowledged_by_user_id_idx
    on public.stock_alerts (acknowledged_by_user_id, acknowledged_at desc)
    where acknowledged_by_user_id is not null;

-- Canh bao theo nguoi xu ly xong.
create index if not exists stock_alerts_resolved_by_user_id_idx
    on public.stock_alerts (resolved_by_user_id, resolved_at desc)
    where resolved_by_user_id is not null;

-- Loc theo kho.
create index if not exists stock_alerts_warehouse_status_idx
    on public.stock_alerts (warehouse_code, status, triggered_at desc);

-- Job retry gui thong bao loi.
create index if not exists stock_alerts_next_retry_idx
    on public.stock_alerts (next_retry_at)
    where status in ('open', 'failed', 'snoozed')
      and next_retry_at is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_stock_alerts_updated_at()
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
        where tgname = 'stock_alerts_set_updated_at'
          and tgrelid = 'public.stock_alerts'::regclass
          and not tgisinternal
    ) then
        create trigger stock_alerts_set_updated_at
        before update on public.stock_alerts
        for each row
        execute function public.set_stock_alerts_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.stock_alerts enable row level security;

revoke all on table public.stock_alerts from anon, authenticated;

grant select, insert, update on table public.stock_alerts to service_role;
grant usage, select on sequence public.stock_alerts_id_seq to service_role;

revoke all on function public.set_stock_alerts_updated_at() from public;
grant execute on function public.set_stock_alerts_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'stock_alerts'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.stock_alerts (
--     alert_code,
--     product_id,
--     product_sku,
--     product_name,
--     unit,
--     alert_type,
--     severity,
--     stock_quantity,
--     minimum_stock,
--     title,
--     message,
--     notification_channel,
--     status
-- ) values (
--     'TK-000001',
--     1,
--     'SP-000001',
--     'Loa JBL Control 28-1',
--     'piece',
--     'low_stock',
--     'high',
--     1,
--     2,
--     'Canh bao ton kho thap: SP-000001',
--     'San pham Loa JBL Control 28-1 chi con 1 piece, duoi nguong toi thieu 2.',
--     'telegram',
--     'open'
-- );

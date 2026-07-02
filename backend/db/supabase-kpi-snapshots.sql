-- Phuc Thanh Audio Group
-- Bang thu muoi hai: kpi_snapshots
-- Chay sau supabase-stock-alerts.sql.
-- Muc tieu: luu ket qua KPI da tinh theo ngay/tuan/thang de bao cao CEO va dashboard.

begin;

create table if not exists public.kpi_snapshots (
    id bigint generated always as identity primary key,

    -- Ma snapshot do Spring Boot sinh, vi du: KPI-2026-W27-COMPANY
    snapshot_code text not null,

    period_type text not null,
    period_start date not null,
    period_end date not null,

    -- company = toan cong ty, department = theo phong ban, staff = theo nhan vien.
    scope_type text not null default 'company',
    department text,
    staff_member_id uuid references public.staff_members (id),

    currency text not null default 'VND',

    -- Sales/pipeline.
    revenue numeric(18, 2) not null default 0,
    pipeline_value numeric(18, 2) not null default 0,
    quotation_value numeric(18, 2) not null default 0,
    contract_value numeric(18, 2) not null default 0,
    receivables numeric(18, 2) not null default 0,
    overdue_amount numeric(18, 2) not null default 0,
    collected_amount numeric(18, 2) not null default 0,
    profit numeric(18, 2) not null default 0,
    gross_margin_rate numeric(5, 2) not null default 0,

    new_leads integer not null default 0,
    won_leads integer not null default 0,
    lost_leads integer not null default 0,
    open_leads integer not null default 0,
    quoted_leads integer not null default 0,
    conversion_rate numeric(5, 2) not null default 0,
    avg_deal_value numeric(18, 2) not null default 0,

    -- CSKH/bao hanh/tong dai.
    completed_repairs integer not null default 0,
    open_repairs integer not null default 0,
    overdue_repairs integer not null default 0,
    call_count integer not null default 0,
    ai_resolved_calls integer not null default 0,
    transferred_calls integer not null default 0,

    -- Kho.
    low_stock_products integer not null default 0,
    out_of_stock_products integer not null default 0,
    active_stock_alerts integer not null default 0,

    generated_at timestamptz not null default now(),
    generated_by_user_id uuid references public.staff_members (id),

    report_status text not null default 'generated',
    notification_channel text not null default 'telegram',
    notification_target text,
    sent_at timestamptz,
    external_message_id text,
    error_message text,

    source_payload jsonb not null default '{}'::jsonb,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint kpi_snapshots_snapshot_code_unique unique (snapshot_code),
    constraint kpi_snapshots_snapshot_code_format_check
        check (
            btrim(snapshot_code) <> ''
            and snapshot_code = upper(btrim(snapshot_code))
        ),
    constraint kpi_snapshots_period_type_check
        check (period_type in ('day', 'week', 'month', 'quarter', 'year', 'custom')),
    constraint kpi_snapshots_period_dates_check
        check (period_end >= period_start),
    constraint kpi_snapshots_scope_type_check
        check (scope_type in ('company', 'department', 'staff')),
    constraint kpi_snapshots_scope_values_check
        check (
            (
                scope_type = 'company'
                and department is null
                and staff_member_id is null
            )
            or (
                scope_type = 'department'
                and department is not null
                and btrim(department) <> ''
                and staff_member_id is null
            )
            or (
                scope_type = 'staff'
                and staff_member_id is not null
            )
        ),
    constraint kpi_snapshots_currency_check
        check (currency in ('VND', 'USD')),
    constraint kpi_snapshots_money_non_negative_check
        check (
            revenue >= 0
            and pipeline_value >= 0
            and quotation_value >= 0
            and contract_value >= 0
            and receivables >= 0
            and overdue_amount >= 0
            and collected_amount >= 0
            and avg_deal_value >= 0
        ),
    constraint kpi_snapshots_rates_check
        check (
            conversion_rate between 0 and 100
            and gross_margin_rate between -100 and 100
        ),
    constraint kpi_snapshots_counts_non_negative_check
        check (
            new_leads >= 0
            and won_leads >= 0
            and lost_leads >= 0
            and open_leads >= 0
            and quoted_leads >= 0
            and completed_repairs >= 0
            and open_repairs >= 0
            and overdue_repairs >= 0
            and call_count >= 0
            and ai_resolved_calls >= 0
            and transferred_calls >= 0
            and low_stock_products >= 0
            and out_of_stock_products >= 0
            and active_stock_alerts >= 0
        ),
    constraint kpi_snapshots_call_counts_check
        check (
            ai_resolved_calls <= call_count
            and transferred_calls <= call_count
        ),
    constraint kpi_snapshots_report_status_check
        check (report_status in ('generated', 'sent', 'failed', 'superseded')),
    constraint kpi_snapshots_notification_channel_check
        check (notification_channel in ('telegram', 'zalo', 'email', 'manual', 'none')),
    constraint kpi_snapshots_notification_target_not_blank_check
        check (
            notification_target is null
            or (btrim(notification_target) <> '' and notification_target = btrim(notification_target))
        ),
    constraint kpi_snapshots_status_dates_check
        check (
            (report_status in ('sent', 'superseded') or sent_at is null)
            and (sent_at is null or sent_at >= generated_at)
        ),
    constraint kpi_snapshots_error_only_when_failed_check
        check (report_status = 'failed' or error_message is null),
    constraint kpi_snapshots_source_payload_object_check
        check (jsonb_typeof(source_payload) = 'object'),
    constraint kpi_snapshots_version_non_negative_check
        check (version >= 0)
);

comment on table public.kpi_snapshots is
    'Snapshot KPI da tinh theo ky. Day la du lieu tong hop cho dashboard va bao cao CEO, khong phai giao dich goc.';
comment on column public.kpi_snapshots.period_type is
    'Ky bao cao: day, week, month, quarter, year hoac custom.';
comment on column public.kpi_snapshots.scope_type is
    'Pham vi KPI: toan cong ty, phong ban hoac tung nhan vien.';
comment on column public.kpi_snapshots.staff_member_id is
    'Nhan vien lien quan neu scope_type = staff.';
comment on column public.kpi_snapshots.revenue is
    'Doanh thu trong ky, thuong lay tu hop dong/MISA.';
comment on column public.kpi_snapshots.pipeline_value is
    'Tong gia tri pipeline dang mo trong ky.';
comment on column public.kpi_snapshots.receivables is
    'Cong no phai thu, thuong dong bo tu MISA.';
comment on column public.kpi_snapshots.profit is
    'Loi nhuan trong ky, thuong dong bo/tinh tu MISA.';
comment on column public.kpi_snapshots.conversion_rate is
    'Ty le chot lead theo %, backend tinh tu won_leads / lead da ket thuc.';
comment on column public.kpi_snapshots.source_payload is
    'Snapshot so lieu nguon tu Airtable/Supabase/MISA/Google Sheets de doi soat.';

-- Mot ky + pham vi chi co mot snapshot hien hanh theo scope.
create unique index if not exists kpi_snapshots_period_scope_unique_idx
    on public.kpi_snapshots (
        period_type,
        period_start,
        period_end,
        scope_type,
        coalesce(department, ''),
        coalesce(staff_member_id::text, '')
    )
    where report_status <> 'superseded';

-- Dashboard CEO xem snapshot moi theo ky.
create index if not exists kpi_snapshots_period_idx
    on public.kpi_snapshots (period_type, period_start desc, period_end desc);

-- Dashboard theo nhan vien.
create index if not exists kpi_snapshots_staff_member_id_idx
    on public.kpi_snapshots (staff_member_id, period_start desc)
    where staff_member_id is not null;

-- Dashboard theo phong ban.
create index if not exists kpi_snapshots_department_idx
    on public.kpi_snapshots (department, period_start desc)
    where department is not null;

-- Job gui/retry bao cao.
create index if not exists kpi_snapshots_report_status_idx
    on public.kpi_snapshots (report_status, generated_at desc);

-- Loc snapshot moi nhat.
create index if not exists kpi_snapshots_generated_at_idx
    on public.kpi_snapshots (generated_at desc);

-- Nguoi tao snapshot.
create index if not exists kpi_snapshots_generated_by_user_id_idx
    on public.kpi_snapshots (generated_by_user_id, generated_at desc)
    where generated_by_user_id is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_kpi_snapshots_updated_at()
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
        where tgname = 'kpi_snapshots_set_updated_at'
          and tgrelid = 'public.kpi_snapshots'::regclass
          and not tgisinternal
    ) then
        create trigger kpi_snapshots_set_updated_at
        before update on public.kpi_snapshots
        for each row
        execute function public.set_kpi_snapshots_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.kpi_snapshots enable row level security;

revoke all on table public.kpi_snapshots from anon, authenticated;

grant select, insert, update on table public.kpi_snapshots to service_role;
grant usage, select on sequence public.kpi_snapshots_id_seq to service_role;

revoke all on function public.set_kpi_snapshots_updated_at() from public;
grant execute on function public.set_kpi_snapshots_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'kpi_snapshots'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.kpi_snapshots (
--     snapshot_code,
--     period_type,
--     period_start,
--     period_end,
--     scope_type,
--     revenue,
--     pipeline_value,
--     new_leads,
--     won_leads,
--     conversion_rate,
--     receivables,
--     profit,
--     completed_repairs,
--     low_stock_products,
--     report_status
-- ) values (
--     'KPI-2026-06-COMPANY',
--     'month',
--     '2026-06-01',
--     '2026-06-30',
--     'company',
--     250000000,
--     500000000,
--     18,
--     5,
--     27.78,
--     80000000,
--     45000000,
--     7,
--     3,
--     'generated'
-- );

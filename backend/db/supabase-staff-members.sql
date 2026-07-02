-- Phuc Thanh Audio Group
-- Bang thu chin: staff_members
-- Chay sau supabase-call-logs.sql.
-- Muc tieu: quan ly nhan su noi bo va gan FK cho cac cot *_user_id da co.

begin;

create table if not exists public.staff_members (
    -- UUID do Spring Boot/Supabase Auth sinh. Co the dung chung voi auth user id neu muon.
    id uuid primary key,

    -- Ma nhan vien do backend sinh, vi du: NV-000001
    staff_code text not null,

    full_name text not null,
    display_name text,
    phone text,
    email text,

    role text not null default 'sales',
    department text not null default 'sales',
    position_title text,

    -- Lien ket tai khoan dang nhap Supabase Auth neu co.
    -- FK toi auth.users duoc bo sung sau trong supabase-user-accounts.sql.
    auth_user_id uuid,

    telegram_chat_id text,
    zalo_user_id text,
    facebook_user_id text,

    status text not null default 'active',
    hired_on date,
    terminated_on date,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint staff_members_staff_code_unique unique (staff_code),
    constraint staff_members_auth_user_id_unique unique (auth_user_id),
    constraint staff_members_email_unique unique (email),
    constraint staff_members_staff_code_format_check
        check (
            btrim(staff_code) <> ''
            and staff_code = upper(btrim(staff_code))
        ),
    constraint staff_members_full_name_not_blank_check
        check (btrim(full_name) <> ''),
    constraint staff_members_display_name_not_blank_check
        check (
            display_name is null
            or (btrim(display_name) <> '' and display_name = btrim(display_name))
        ),
    constraint staff_members_phone_not_blank_check
        check (
            phone is null
            or (btrim(phone) <> '' and phone = btrim(phone))
        ),
    constraint staff_members_email_format_check
        check (
            email is null
            or (
                btrim(email) <> ''
                and email = lower(btrim(email))
                and email like '%@%'
            )
        ),
    constraint staff_members_role_check
        check (
            role in (
                'ceo',
                'admin',
                'manager',
                'sales',
                'technician',
                'accountant',
                'warehouse',
                'support',
                'operator',
                'other'
            )
        ),
    constraint staff_members_department_check
        check (
            department in (
                'executive',
                'sales',
                'technical',
                'accounting',
                'warehouse',
                'support',
                'operations',
                'other'
            )
        ),
    constraint staff_members_status_check
        check (status in ('active', 'inactive', 'resigned', 'blocked')),
    constraint staff_members_dates_check
        check (
            hired_on is null
            or terminated_on is null
            or terminated_on >= hired_on
        ),
    constraint staff_members_version_non_negative_check
        check (version >= 0)
);

comment on table public.staff_members is
    'Nhan su noi bo: sales, KTV, ke toan, kho, tong dai, CEO/admin.';
comment on column public.staff_members.id is
    'UUID nhan vien noi bo; cac bang nghiep vu tham chieu cot nay.';
comment on column public.staff_members.auth_user_id is
    'UUID tai khoan Supabase Auth neu nhan vien co dang nhap he thong.';
comment on column public.staff_members.telegram_chat_id is
    'ID Telegram de gui thong bao noi bo cho tung nhan vien/bo phan.';
comment on column public.staff_members.status is
    'Khong xoa nhan vien cu; doi status thanh inactive/resigned/blocked.';

-- Loc danh sach nhan vien theo vai tro va trang thai.
create index if not exists staff_members_role_status_idx
    on public.staff_members (role, status);

-- Loc danh sach theo phong ban.
create index if not exists staff_members_department_status_idx
    on public.staff_members (department, status);

-- Tim nhan vien theo so dien thoai.
create index if not exists staff_members_phone_idx
    on public.staff_members (phone)
    where phone is not null;

-- Tim nhan vien theo Telegram khi gui thong bao noi bo.
create index if not exists staff_members_telegram_chat_id_idx
    on public.staff_members (telegram_chat_id)
    where telegram_chat_id is not null;

-- Bo sung FK cho cac cot nhan vien da tao o cac phase truoc.
do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'leads_assigned_to_user_id_fkey'
          and conrelid = 'public.leads'::regclass
    ) then
        alter table public.leads
        add constraint leads_assigned_to_user_id_fkey
        foreign key (assigned_to_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'quotations_created_by_user_id_fkey'
          and conrelid = 'public.quotations'::regclass
    ) then
        alter table public.quotations
        add constraint quotations_created_by_user_id_fkey
        foreign key (created_by_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'contracts_created_by_user_id_fkey'
          and conrelid = 'public.contracts'::regclass
    ) then
        alter table public.contracts
        add constraint contracts_created_by_user_id_fkey
        foreign key (created_by_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'repair_requests_technician_user_id_fkey'
          and conrelid = 'public.repair_requests'::regclass
    ) then
        alter table public.repair_requests
        add constraint repair_requests_technician_user_id_fkey
        foreign key (technician_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'customer_reminders_assigned_to_user_id_fkey'
          and conrelid = 'public.customer_reminders'::regclass
    ) then
        alter table public.customer_reminders
        add constraint customer_reminders_assigned_to_user_id_fkey
        foreign key (assigned_to_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'call_logs_transferred_to_user_id_fkey'
          and conrelid = 'public.call_logs'::regclass
    ) then
        alter table public.call_logs
        add constraint call_logs_transferred_to_user_id_fkey
        foreign key (transferred_to_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'call_logs_handled_by_user_id_fkey'
          and conrelid = 'public.call_logs'::regclass
    ) then
        alter table public.call_logs
        add constraint call_logs_handled_by_user_id_fkey
        foreign key (handled_by_user_id)
        references public.staff_members (id)
        on update cascade
        on delete set null;
    end if;
end;
$$;

-- Index cho cac FK vua bo sung. Mot so bang da co index rieng tu phase truoc.
create index if not exists leads_assigned_to_user_id_idx
    on public.leads (assigned_to_user_id)
    where assigned_to_user_id is not null;

create index if not exists quotations_created_by_user_id_idx
    on public.quotations (created_by_user_id)
    where created_by_user_id is not null;

create index if not exists contracts_created_by_user_id_idx
    on public.contracts (created_by_user_id)
    where created_by_user_id is not null;

create index if not exists repair_requests_technician_user_id_idx
    on public.repair_requests (technician_user_id)
    where technician_user_id is not null;

create index if not exists customer_reminders_assigned_to_user_id_idx
    on public.customer_reminders (assigned_to_user_id)
    where assigned_to_user_id is not null;

create index if not exists call_logs_transferred_to_user_id_idx
    on public.call_logs (transferred_to_user_id)
    where transferred_to_user_id is not null;

create index if not exists call_logs_handled_by_user_id_idx
    on public.call_logs (handled_by_user_id)
    where handled_by_user_id is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_staff_members_updated_at()
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
        where tgname = 'staff_members_set_updated_at'
          and tgrelid = 'public.staff_members'::regclass
          and not tgisinternal
    ) then
        create trigger staff_members_set_updated_at
        before update on public.staff_members
        for each row
        execute function public.set_staff_members_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.staff_members enable row level security;

revoke all on table public.staff_members from anon, authenticated;

grant select, insert, update on table public.staff_members to service_role;

revoke all on function public.set_staff_members_updated_at() from public;
grant execute on function public.set_staff_members_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'staff_members'
order by ordinal_position;

-- Kiem tra FK da gan voi staff_members.
select
    conrelid::regclass as table_name,
    conname as constraint_name,
    pg_get_constraintdef(oid) as definition
from pg_constraint
where contype = 'f'
  and confrelid = 'public.staff_members'::regclass
order by conrelid::regclass::text, conname;

-- Du lieu test tuy chon:
-- insert into public.staff_members (
--     id,
--     staff_code,
--     full_name,
--     display_name,
--     phone,
--     email,
--     role,
--     department,
--     position_title,
--     telegram_chat_id,
--     status
-- ) values (
--     '11111111-1111-1111-1111-111111111111',
--     'NV-000001',
--     'Nguyen Van Sales',
--     'Sales A',
--     '0901234567',
--     'sales.a@example.com',
--     'sales',
--     'sales',
--     'Nhan vien kinh doanh',
--     '123456789',
--     'active'
-- );

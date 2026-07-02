-- Phuc Thanh Audio Group
-- Bang thu muoi lam: user_accounts + customer_memberships + auth_audit_logs
-- Chay sau supabase-payment-records.sql (va sau customers/staff_members).
-- Muc tieu: lien ket Supabase Auth voi tai khoan nghiep vu va phan quyen theo khach hang.
-- Password, refresh token va MFA factor do Supabase Auth quan ly trong schema auth;
-- tuyet doi khong luu cac gia tri nay trong schema public.

begin;

create table if not exists public.user_accounts (
    -- Dung cung UUID voi auth.users.id.
    id uuid primary key references auth.users (id) on delete cascade,

    account_type text not null,
    role text not null default 'CUSTOMER',
    status text not null default 'pending',

    display_name text,
    avatar_url text,
    locale text not null default 'vi-VN',
    time_zone text not null default 'Asia/Ho_Chi_Minh',

    -- Bat buoc MFA cho nhom nhan vien nhay cam; backend kiem tra JWT claim aal.
    mfa_required boolean not null default false,

    last_login_at timestamptz,
    last_seen_at timestamptz,
    locked_at timestamptz,
    disabled_at timestamptz,
    lock_reason text,

    -- Tang session_version khi can vo hieu hoa logic tat ca session o backend.
    session_version bigint not null default 0,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint user_accounts_type_check
        check (account_type in ('customer', 'staff', 'hybrid')),
    constraint user_accounts_role_check
        check (role in ('ADMIN', 'SALES', 'CUSTOMER')),
    constraint user_accounts_status_check
        check (status in ('pending', 'active', 'locked', 'disabled')),
    constraint user_accounts_display_name_not_blank_check
        check (
            display_name is null
            or (
                btrim(display_name) <> ''
                and display_name = btrim(display_name)
            )
        ),
    constraint user_accounts_locale_not_blank_check
        check (btrim(locale) <> '' and locale = btrim(locale)),
    constraint user_accounts_time_zone_not_blank_check
        check (btrim(time_zone) <> '' and time_zone = btrim(time_zone)),
    constraint user_accounts_locked_at_check
        check (status = 'locked' or locked_at is null),
    constraint user_accounts_disabled_at_check
        check (status = 'disabled' or disabled_at is null),
    constraint user_accounts_lock_reason_check
        check (
            lock_reason is null
            or (
                status in ('locked', 'disabled')
                and btrim(lock_reason) <> ''
                and lock_reason = btrim(lock_reason)
            )
        ),
    constraint user_accounts_session_version_non_negative_check
        check (session_version >= 0),
    constraint user_accounts_version_non_negative_check
        check (version >= 0)
);

-- Backfill role when this script is re-run against an existing installation.
alter table public.user_accounts
    add column if not exists role text;

update public.user_accounts ua
set role = case
    when sm.role = 'admin' then 'ADMIN'
    when sm.role = 'sales' then 'SALES'
    else 'CUSTOMER'
end
from public.staff_members sm
where sm.auth_user_id = ua.id
  and ua.role is null;

update public.user_accounts
set role = 'CUSTOMER'
where role is null;

alter table public.user_accounts
    alter column role set default 'CUSTOMER',
    alter column role set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'user_accounts_role_check'
          and conrelid = 'public.user_accounts'::regclass
    ) then
        alter table public.user_accounts
        add constraint user_accounts_role_check
        check (role in ('ADMIN', 'SALES', 'CUSTOMER'));
    end if;
end;
$$;

comment on table public.user_accounts is
    'Ho so tai khoan nghiep vu gan 1-1 voi auth.users; khong chua password/token.';
comment on column public.user_accounts.id is
    'UUID giong auth.users.id; xoa Auth user se xoa profile va membership lien quan.';
comment on column public.user_accounts.account_type is
    'Customer, staff hoac hybrid neu mot nguoi dong thoi co ca hai vai tro.';
comment on column public.user_accounts.role is
    'Vai tro ung dung dua vao JWT custom claim: ADMIN, SALES hoac CUSTOMER.';
comment on column public.user_accounts.mfa_required is
    'Backend bat buoc session aal2 cho nhom vai tro nhay cam.';
comment on column public.user_accounts.session_version is
    'Phien ban session logic; backend co the so sanh de vo hieu hoa quyen ngay lap tuc.';

create index if not exists user_accounts_status_type_idx
    on public.user_accounts (status, account_type);

create index if not exists user_accounts_role_status_idx
    on public.user_accounts (role, status);

create index if not exists user_accounts_last_seen_idx
    on public.user_accounts (last_seen_at desc)
    where status = 'active'
      and last_seen_at is not null;

create table if not exists public.customer_memberships (
    id bigint generated always as identity primary key,

    user_id uuid not null references public.user_accounts (id) on delete cascade,
    customer_id bigint not null references public.customers (id) on delete cascade,

    role text not null default 'viewer',
    status text not null default 'invited',

    invited_by_user_id uuid references public.user_accounts (id) on delete set null,
    invited_at timestamptz not null default now(),
    accepted_at timestamptz,
    revoked_at timestamptz,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint customer_memberships_user_customer_unique
        unique (user_id, customer_id),
    constraint customer_memberships_role_check
        check (role in ('owner', 'buyer', 'accounting', 'viewer')),
    constraint customer_memberships_status_check
        check (status in ('invited', 'active', 'revoked')),
    constraint customer_memberships_status_dates_check
        check (
            (
                status = 'invited'
                and accepted_at is null
                and revoked_at is null
            )
            or (
                status = 'active'
                and accepted_at is not null
                and revoked_at is null
            )
            or (
                status = 'revoked'
                and revoked_at is not null
            )
        ),
    constraint customer_memberships_dates_order_check
        check (
            (accepted_at is null or accepted_at >= invited_at)
            and (revoked_at is null or revoked_at >= invited_at)
        ),
    constraint customer_memberships_version_non_negative_check
        check (version >= 0)
);

comment on table public.customer_memberships is
    'Gan tai khoan dang nhap voi khach hang/doanh nghiep va vai tro trong to chuc.';
comment on column public.customer_memberships.user_id is
    'Tai khoan co quyen truy cap ho so customer.';
comment on column public.customer_memberships.customer_id is
    'Mot doanh nghiep co the co nhieu tai khoan owner/buyer/accounting/viewer.';
comment on column public.customer_memberships.invited_by_user_id is
    'Tai khoan nhan vien/admin/owner gui loi moi; nullable cho self-registration da duyet.';

-- Tim tat ca customer ma mot user duoc phep truy cap.
create index if not exists customer_memberships_user_status_idx
    on public.customer_memberships (user_id, status, customer_id);

-- Man hinh quan ly thanh vien cua doanh nghiep.
create index if not exists customer_memberships_customer_status_role_idx
    on public.customer_memberships (customer_id, status, role);

create index if not exists customer_memberships_invited_by_user_id_idx
    on public.customer_memberships (invited_by_user_id)
    where invited_by_user_id is not null;

create table if not exists public.auth_audit_logs (
    id bigint generated always as identity primary key,

    -- Set null de giu audit log khi Auth user bi xoa.
    user_id uuid references auth.users (id) on delete set null,
    actor_user_id uuid references auth.users (id) on delete set null,

    event_type text not null,
    source text not null default 'api',
    success boolean not null default true,

    request_id text,
    session_id text,
    ip_address inet,
    user_agent text,
    failure_code text,
    metadata jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null default now(),

    constraint auth_audit_logs_event_type_check
        check (
            event_type in (
                'signup',
                'email_verified',
                'login_success',
                'login_failed',
                'logout',
                'password_reset_requested',
                'password_changed',
                'mfa_enrolled',
                'mfa_challenged',
                'mfa_verified',
                'account_locked',
                'account_unlocked',
                'account_disabled',
                'invite_sent',
                'invite_accepted',
                'session_revoked',
                'other'
            )
        ),
    constraint auth_audit_logs_source_check
        check (source in ('web', 'mobile', 'admin', 'api', 'system')),
    constraint auth_audit_logs_request_id_not_blank_check
        check (
            request_id is null
            or (
                btrim(request_id) <> ''
                and request_id = btrim(request_id)
            )
        ),
    constraint auth_audit_logs_session_id_not_blank_check
        check (
            session_id is null
            or (
                btrim(session_id) <> ''
                and session_id = btrim(session_id)
            )
        ),
    constraint auth_audit_logs_failure_code_check
        check (
            failure_code is null
            or (
                success = false
                and btrim(failure_code) <> ''
                and failure_code = btrim(failure_code)
            )
        ),
    constraint auth_audit_logs_metadata_object_check
        check (jsonb_typeof(metadata) = 'object')
);

comment on table public.auth_audit_logs is
    'Audit bat bien cho signup/login/logout/password/MFA/invite/lock; khong luu password hay token.';
comment on column public.auth_audit_logs.actor_user_id is
    'Nguoi thuc hien hanh dong neu la admin/manager; co the khac user_id.';
comment on column public.auth_audit_logs.metadata is
    'Chi luu metadata an toan; cam luu access token, refresh token, OTP hoac password.';

create index if not exists auth_audit_logs_user_occurred_at_idx
    on public.auth_audit_logs (user_id, occurred_at desc)
    where user_id is not null;

create index if not exists auth_audit_logs_actor_occurred_at_idx
    on public.auth_audit_logs (actor_user_id, occurred_at desc)
    where actor_user_id is not null;

create index if not exists auth_audit_logs_event_occurred_at_idx
    on public.auth_audit_logs (event_type, occurred_at desc);

create index if not exists auth_audit_logs_failed_occurred_at_idx
    on public.auth_audit_logs (occurred_at desc)
    where success = false;

create index if not exists auth_audit_logs_request_id_idx
    on public.auth_audit_logs (request_id)
    where request_id is not null;

-- staff_members ton tai truoc phase nay. Giu ho so nhan vien khi Auth user bi xoa.
do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'staff_members_auth_user_id_fkey'
          and conrelid = 'public.staff_members'::regclass
    ) then
        alter table public.staff_members
        add constraint staff_members_auth_user_id_fkey
        foreign key (auth_user_id)
        references auth.users (id)
        on delete set null;
    end if;
end;
$$;

comment on column public.staff_members.auth_user_id is
    'UUID tai khoan auth.users; FK duoc bo sung boi supabase-user-accounts.sql.';

-- Tu dong cap nhat updated_at cho hai bang co the thay doi.
create or replace function public.set_user_accounts_updated_at()
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

create or replace function public.set_customer_memberships_updated_at()
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

-- Supabase Auth hook: dua role tu bang nghiep vu vao access-token JWT.
-- Neu profile chua kip tao trong luc signup, quyen an toan mac dinh la CUSTOMER.
create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
    claims jsonb;
    account_role text;
begin
    select ua.role
    into account_role
    from public.user_accounts ua
    where ua.id = (event ->> 'user_id')::uuid;

    account_role := coalesce(account_role, 'CUSTOMER');
    claims := event -> 'claims';
    claims := jsonb_set(claims, '{user_role}', to_jsonb(account_role), true);

    return jsonb_set(event, '{claims}', claims, true);
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'user_accounts_set_updated_at'
          and tgrelid = 'public.user_accounts'::regclass
          and not tgisinternal
    ) then
        create trigger user_accounts_set_updated_at
        before update on public.user_accounts
        for each row
        execute function public.set_user_accounts_updated_at();
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_trigger
        where tgname = 'customer_memberships_set_updated_at'
          and tgrelid = 'public.customer_memberships'::regclass
          and not tgisinternal
    ) then
        create trigger customer_memberships_set_updated_at
        before update on public.customer_memberships
        for each row
        execute function public.set_customer_memberships_updated_at();
    end if;
end;
$$;

-- Frontend chi dung Supabase Auth; moi nghiep vu profile/quyen di qua User/CRM Service.
alter table public.user_accounts enable row level security;
alter table public.customer_memberships enable row level security;
alter table public.auth_audit_logs enable row level security;

revoke all on table public.user_accounts from anon, authenticated;
revoke all on table public.customer_memberships from anon, authenticated;
revoke all on table public.auth_audit_logs from anon, authenticated;

grant select, insert, update on table public.user_accounts to service_role;
grant select, insert, update on table public.customer_memberships to service_role;

-- Audit log append-only: backend khong duoc update/delete.
grant select, insert on table public.auth_audit_logs to service_role;

grant usage, select on sequence public.customer_memberships_id_seq to service_role;
grant usage, select on sequence public.auth_audit_logs_id_seq to service_role;

grant usage on schema public to supabase_auth_admin;
grant select (id, role) on table public.user_accounts to supabase_auth_admin;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'user_accounts'
          and policyname = 'Supabase Auth can read account roles'
    ) then
        create policy "Supabase Auth can read account roles"
        on public.user_accounts
        for select
        to supabase_auth_admin
        using (true);
    end if;
end;
$$;

revoke all on function public.set_user_accounts_updated_at() from public;
revoke all on function public.set_customer_memberships_updated_at() from public;
revoke all on function public.custom_access_token_hook(jsonb) from public, anon, authenticated;
grant execute on function public.set_user_accounts_updated_at() to service_role;
grant execute on function public.set_customer_memberships_updated_at() to service_role;
grant execute on function public.custom_access_token_hook(jsonb) to supabase_auth_admin;

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
  and table_name in ('user_accounts', 'customer_memberships', 'auth_audit_logs')
order by table_name, ordinal_position;

-- Kiem tra RLS.
select
    c.relname as table_name,
    c.relrowsecurity as rls_enabled
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('user_accounts', 'customer_memberships', 'auth_audit_logs')
order by c.relname;

-- Cach tao du lieu dung:
-- 1. Customer tu dang ky qua /api/auth/register va luon nhan role CUSTOMER.
-- 2. ADMIN dau tien duoc bootstrap mot lan bang AUTH_BOOTSTRAP_ADMIN_EMAIL.
-- 3. ADMIN moi SALES qua /api/auth/admin/sales; backend goi Supabase Auth Admin API.
-- 4. Backend tao user_accounts va staff_members trong cung transaction nghiep vu.
-- Khong insert truc tiep password vao auth.users va khong tao trigger signup trong file nay.

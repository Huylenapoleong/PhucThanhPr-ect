-- Phuc Thanh Audio Group
-- Bang thu muoi: inventory_movements
-- Chay sau supabase-staff-members.sql.
-- Muc tieu: luu lich su nhap/xuat/tra hang/dieu chinh kho va doi soat ton kho.

begin;

create table if not exists public.inventory_movements (
    id bigint generated always as identity primary key,

    -- Ma phieu kho do Spring Boot sinh, vi du: KHO-000001
    movement_code text not null,

    product_id bigint not null references public.products (id),
    contract_id bigint references public.contracts (id),
    repair_request_id bigint references public.repair_requests (id),
    customer_asset_id bigint references public.customer_assets (id),
    customer_id bigint references public.customers (id),

    performed_by_user_id uuid references public.staff_members (id),
    approved_by_user_id uuid references public.staff_members (id),

    -- Kho hien tai dung 1 kho MAIN; sau nay co the tach bang warehouses neu can.
    warehouse_code text not null default 'MAIN',

    movement_type text not null,
    movement_direction text not null,
    quantity numeric(14, 3) not null,
    quantity_delta numeric(14, 3) generated always as (
        case
            when movement_direction = 'in' then quantity
            else -quantity
        end
    ) stored,
    unit text not null default 'piece',

    -- Ton truoc/sau do backend tinh trong cung transaction voi update products.stock_quantity.
    stock_before numeric(14, 3) not null,
    stock_after numeric(14, 3) not null,

    unit_cost numeric(18, 2) not null default 0,
    unit_price numeric(18, 2) not null default 0,
    cost_amount numeric(18, 2) generated always as (
        quantity * unit_cost
    ) stored,
    sale_amount numeric(18, 2) generated always as (
        quantity * unit_price
    ) stored,

    movement_at timestamptz not null default now(),
    posted_at timestamptz not null default now(),

    source_type text not null default 'manual',
    source_ref text,
    document_number text,

    serial_number text,
    batch_number text,

    status text not null default 'posted',
    reversal_of_movement_id bigint references public.inventory_movements (id),
    voided_at timestamptz,
    void_reason text,

    external_payload jsonb not null default '{}'::jsonb,
    notes text,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint inventory_movements_movement_code_unique unique (movement_code),
    constraint inventory_movements_movement_code_format_check
        check (
            btrim(movement_code) <> ''
            and movement_code = upper(btrim(movement_code))
        ),
    constraint inventory_movements_warehouse_code_format_check
        check (
            btrim(warehouse_code) <> ''
            and warehouse_code = upper(btrim(warehouse_code))
        ),
    constraint inventory_movements_type_check
        check (
            movement_type in (
                'purchase_in',
                'customer_return_in',
                'repair_return_in',
                'stock_adjustment_in',
                'stocktake_in',
                'transfer_in',
                'other_in',
                'sale_out',
                'warranty_out',
                'repair_out',
                'supplier_return_out',
                'damaged_out',
                'stock_adjustment_out',
                'stocktake_out',
                'transfer_out',
                'other_out'
            )
        ),
    constraint inventory_movements_direction_check
        check (movement_direction in ('in', 'out')),
    constraint inventory_movements_type_direction_check
        check (
            (
                movement_direction = 'in'
                and movement_type in (
                    'purchase_in',
                    'customer_return_in',
                    'repair_return_in',
                    'stock_adjustment_in',
                    'stocktake_in',
                    'transfer_in',
                    'other_in'
                )
            )
            or (
                movement_direction = 'out'
                and movement_type in (
                    'sale_out',
                    'warranty_out',
                    'repair_out',
                    'supplier_return_out',
                    'damaged_out',
                    'stock_adjustment_out',
                    'stocktake_out',
                    'transfer_out',
                    'other_out'
                )
            )
        ),
    constraint inventory_movements_quantity_positive_check
        check (quantity > 0),
    constraint inventory_movements_unit_check
        check (unit in ('piece', 'set', 'box', 'meter')),
    constraint inventory_movements_stock_non_negative_check
        check (stock_before >= 0 and stock_after >= 0),
    constraint inventory_movements_stock_after_matches_check
        check (
            stock_after = stock_before + case
                when movement_direction = 'in' then quantity
                else -quantity
            end
        ),
    constraint inventory_movements_money_non_negative_check
        check (unit_cost >= 0 and unit_price >= 0),
    constraint inventory_movements_source_type_check
        check (
            source_type in (
                'purchase',
                'sale',
                'contract',
                'repair',
                'warranty',
                'return',
                'stocktake',
                'adjustment',
                'transfer',
                'misa',
                'google_sheet',
                'manual',
                'other'
            )
        ),
    constraint inventory_movements_source_ref_not_blank_check
        check (
            source_ref is null
            or (btrim(source_ref) <> '' and source_ref = btrim(source_ref))
        ),
    constraint inventory_movements_document_number_not_blank_check
        check (
            document_number is null
            or (btrim(document_number) <> '' and document_number = btrim(document_number))
        ),
    constraint inventory_movements_serial_number_not_blank_check
        check (
            serial_number is null
            or (btrim(serial_number) <> '' and serial_number = btrim(serial_number))
        ),
    constraint inventory_movements_batch_number_not_blank_check
        check (
            batch_number is null
            or (btrim(batch_number) <> '' and batch_number = btrim(batch_number))
        ),
    constraint inventory_movements_source_relation_check
        check (
            (source_type <> 'contract' or contract_id is not null)
            and (source_type not in ('repair', 'warranty') or repair_request_id is not null)
        ),
    constraint inventory_movements_status_check
        check (status in ('posted', 'voided')),
    constraint inventory_movements_voided_check
        check (
            (status = 'voided' or voided_at is null)
            and (status = 'voided' or void_reason is null)
            and (void_reason is null or btrim(void_reason) <> '')
        ),
    constraint inventory_movements_reversal_not_self_check
        check (reversal_of_movement_id is null or reversal_of_movement_id <> id),
    constraint inventory_movements_external_payload_object_check
        check (jsonb_typeof(external_payload) = 'object'),
    constraint inventory_movements_version_non_negative_check
        check (version >= 0)
);

comment on table public.inventory_movements is
    'Lich su nhap/xuat/dieu chinh kho. Backend cap nhat products.stock_quantity trong cung transaction.';
comment on column public.inventory_movements.product_id is
    'San pham duoc nhap/xuat/dieu chinh.';
comment on column public.inventory_movements.contract_id is
    'Hop dong lien quan khi xuat ban/giao hang.';
comment on column public.inventory_movements.repair_request_id is
    'Phieu bao hanh/sua chua lien quan khi xuat linh kien hoac nhap tra sau sua.';
comment on column public.inventory_movements.customer_asset_id is
    'Thiet bi/serial lien quan neu movement gan voi tai san cua khach.';
comment on column public.inventory_movements.performed_by_user_id is
    'Nhan vien thuc hien nhap/xuat/dieu chinh kho.';
comment on column public.inventory_movements.quantity_delta is
    'So luong tac dong vao ton kho: nhap la duong, xuat la am.';
comment on column public.inventory_movements.stock_before is
    'Ton kho truoc movement, backend tinh khi ghi movement.';
comment on column public.inventory_movements.stock_after is
    'Ton kho sau movement; phai khop stock_before +/- quantity.';
comment on column public.inventory_movements.reversal_of_movement_id is
    'Neu can sua sai, tao movement dao chieu va tro ve movement goc thay vi xoa dong cu.';
comment on column public.inventory_movements.external_payload is
    'Snapshot du lieu nguon tu MISA, Google Sheets, nha cung cap hoac he thong ngoai.';

-- Lich su kho theo san pham moi nhat.
create index if not exists inventory_movements_product_at_idx
    on public.inventory_movements (product_id, movement_at desc);

-- Man hinh nhat ky kho theo thoi gian.
create index if not exists inventory_movements_at_idx
    on public.inventory_movements (movement_at desc);

-- Loc theo kho va san pham.
create index if not exists inventory_movements_warehouse_product_idx
    on public.inventory_movements (warehouse_code, product_id, movement_at desc);

-- Loc nhap/xuat theo loai.
create index if not exists inventory_movements_type_at_idx
    on public.inventory_movements (movement_type, movement_at desc);

-- Lich su xuat kho theo hop dong.
create index if not exists inventory_movements_contract_id_idx
    on public.inventory_movements (contract_id)
    where contract_id is not null;

-- Lich su linh kien/thiet bi theo phieu sua chua.
create index if not exists inventory_movements_repair_request_id_idx
    on public.inventory_movements (repair_request_id)
    where repair_request_id is not null;

-- Movement lien quan tai san/serial cua khach.
create index if not exists inventory_movements_customer_asset_id_idx
    on public.inventory_movements (customer_asset_id)
    where customer_asset_id is not null;

-- Movement theo khach.
create index if not exists inventory_movements_customer_id_idx
    on public.inventory_movements (customer_id)
    where customer_id is not null;

-- Movement theo nhan vien thuc hien.
create index if not exists inventory_movements_performed_by_user_id_idx
    on public.inventory_movements (performed_by_user_id, movement_at desc)
    where performed_by_user_id is not null;

-- Movement theo nhan vien duyet.
create index if not exists inventory_movements_approved_by_user_id_idx
    on public.inventory_movements (approved_by_user_id, movement_at desc)
    where approved_by_user_id is not null;

-- Doi soat chung tu nguon.
create index if not exists inventory_movements_source_idx
    on public.inventory_movements (source_type, source_ref)
    where source_ref is not null;

-- Tra cuu serial neu can doi soat thiet bi.
create index if not exists inventory_movements_serial_number_idx
    on public.inventory_movements (serial_number)
    where serial_number is not null;

-- Movement dao chieu.
create index if not exists inventory_movements_reversal_of_idx
    on public.inventory_movements (reversal_of_movement_id)
    where reversal_of_movement_id is not null;

-- Tu dong cap nhat updated_at.
create or replace function public.set_inventory_movements_updated_at()
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
        where tgname = 'inventory_movements_set_updated_at'
          and tgrelid = 'public.inventory_movements'::regclass
          and not tgisinternal
    ) then
        create trigger inventory_movements_set_updated_at
        before update on public.inventory_movements
        for each row
        execute function public.set_inventory_movements_updated_at();
    end if;
end;
$$;

-- React goi Spring Boot; khong cho browser doc/ghi bang truc tiep.
alter table public.inventory_movements enable row level security;

revoke all on table public.inventory_movements from anon, authenticated;

grant select, insert, update on table public.inventory_movements to service_role;
grant usage, select on sequence public.inventory_movements_id_seq to service_role;

revoke all on function public.set_inventory_movements_updated_at() from public;
grant execute on function public.set_inventory_movements_updated_at() to service_role;

commit;

-- Kiem tra schema sau khi chay.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'inventory_movements'
order by ordinal_position;

-- Du lieu test tuy chon:
-- insert into public.inventory_movements (
--     movement_code,
--     product_id,
--     performed_by_user_id,
--     movement_type,
--     movement_direction,
--     quantity,
--     unit,
--     stock_before,
--     stock_after,
--     unit_cost,
--     source_type,
--     source_ref,
--     notes
-- ) values (
--     'KHO-000001',
--     1,
--     '11111111-1111-1111-1111-111111111111',
--     'purchase_in',
--     'in',
--     5,
--     'piece',
--     7,
--     12,
--     12000000,
--     'purchase',
--     'PN-000001',
--     'Nhap them loa JBL ve kho MAIN'
-- );

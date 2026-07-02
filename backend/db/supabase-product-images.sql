-- Phuc Thanh Audio Group
-- Phase 16: product_images
-- Chay sau supabase-user-accounts.sql.
-- File nay chi tao metadata nghiep vu; bucket phai tao qua Supabase Storage API/Dashboard.

begin;

create table if not exists public.product_images (
    id bigint generated always as identity primary key,

    product_id bigint not null,

    -- Khong luu public URL co dinh. Backend dung provider, bucket va object_key
    -- de tao URL/CDN URL, giup co the doi nha cung cap Storage sau nay.
    storage_provider text not null default 'supabase_storage',
    bucket_name text not null default 'product-media',
    object_key text not null,

    original_file_name text not null,
    mime_type text not null,
    file_size_bytes bigint not null,
    width_pixels integer,
    height_pixels integer,
    checksum_sha256 text,

    alt_text text,
    sort_order integer not null default 0,
    is_primary boolean not null default false,
    status text not null default 'active',

    uploaded_by_staff_id uuid,

    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint product_images_product_id_fkey
        foreign key (product_id)
        references public.products (id)
        on update cascade
        on delete restrict,
    constraint product_images_uploaded_by_staff_id_fkey
        foreign key (uploaded_by_staff_id)
        references public.staff_members (id)
        on update cascade
        on delete set null,
    constraint product_images_object_unique
        unique (storage_provider, bucket_name, object_key),
    constraint product_images_storage_provider_check
        check (storage_provider in ('supabase_storage', 's3')),
    constraint product_images_bucket_name_check
        check (
            btrim(bucket_name) <> ''
            and bucket_name = btrim(bucket_name)
        ),
    constraint product_images_object_key_check
        check (
            object_key = btrim(object_key)
            and object_key ~ (
                '^products/'
                || product_id::text
                || '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-'
                || '[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|jpeg|png|webp|avif)$'
            )
        ),
    constraint product_images_original_file_name_check
        check (
            btrim(original_file_name) <> ''
            and original_file_name = btrim(original_file_name)
        ),
    constraint product_images_mime_type_check
        check (
            mime_type in (
                'image/jpeg',
                'image/png',
                'image/webp',
                'image/avif'
            )
        ),
    constraint product_images_file_size_check
        check (file_size_bytes between 1 and 10485760),
    constraint product_images_dimensions_check
        check (
            (width_pixels is null and height_pixels is null)
            or (
                width_pixels is not null
                and height_pixels is not null
                and width_pixels > 0
                and height_pixels > 0
            )
        ),
    constraint product_images_checksum_sha256_check
        check (
            checksum_sha256 is null
            or checksum_sha256 ~ '^[0-9a-f]{64}$'
        ),
    constraint product_images_alt_text_check
        check (
            alt_text is null
            or (
                btrim(alt_text) <> ''
                and alt_text = btrim(alt_text)
                and char_length(alt_text) <= 500
            )
        ),
    constraint product_images_sort_order_check
        check (sort_order >= 0),
    constraint product_images_status_check
        check (status in ('processing', 'active', 'failed', 'archived')),
    constraint product_images_version_non_negative_check
        check (version >= 0)
);

comment on table public.product_images is
    'Metadata anh san pham; file nhi phan nam trong Supabase Storage/S3-compatible storage.';
comment on column public.product_images.object_key is
    'Object key bat buoc theo mau products/{product_id}/{uuid}.{ext}.';
comment on column public.product_images.is_primary is
    'Moi san pham chi co toi da mot anh primary dang active.';
comment on column public.product_images.status is
    'processing, active, failed hoac archived; khong xoa cung metadata anh.';
comment on column public.products.image_url is
    'Cot legacy cho mot URL anh; anh moi quan ly qua product_images.';

-- Danh sach anh theo san pham; dong thoi bao phu FK product_id.
create index if not exists product_images_product_status_display_idx
    on public.product_images (product_id, status, sort_order, id);

-- Dam bao moi san pham chi co mot anh chinh dang hien thi.
create unique index if not exists product_images_one_active_primary_idx
    on public.product_images (product_id)
    where is_primary and status = 'active';

-- Index cho FK nhan vien upload.
create index if not exists product_images_uploaded_by_staff_id_idx
    on public.product_images (uploaded_by_staff_id)
    where uploaded_by_staff_id is not null;

-- Phuc vu worker don file loi/anh da archive sau thoi gian luu tru.
create index if not exists product_images_cleanup_idx
    on public.product_images (status, updated_at)
    where status in ('processing', 'failed', 'archived');

create or replace function public.set_product_images_updated_at()
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
        where tgname = 'product_images_set_updated_at'
          and tgrelid = 'public.product_images'::regclass
          and not tgisinternal
    ) then
        create trigger product_images_set_updated_at
        before update on public.product_images
        for each row
        execute function public.set_product_images_updated_at();
    end if;
end;
$$;

-- Frontend chi doc metadata qua Catalog/Inventory Service.
alter table public.product_images enable row level security;

revoke all on table public.product_images from anon, authenticated;
-- Khong grant DELETE: archive metadata, sau do worker xoa object bang Storage API.
grant select, insert, update on table public.product_images to service_role;
grant usage, select on sequence public.product_images_id_seq to service_role;

revoke all on function public.set_product_images_updated_at() from public;
grant execute on function public.set_product_images_updated_at() to service_role;

commit;

-- Bucket tao bang Supabase Dashboard > Storage:
--   Name: product-media
--   Public bucket: true
--   File size limit: 10 MB
--   Allowed MIME: image/jpeg, image/png, image/webp, image/avif
--
-- Khong INSERT/UPDATE truc tiep storage.buckets hoac storage.objects.
-- Upload/delete object phai qua Storage API/S3 API tu backend.
-- Backend dung secret/service credential; khong dua credential nay vao frontend.

-- Kiem tra bang metadata.
select
    column_name,
    data_type,
    is_nullable,
    column_default
from information_schema.columns
where table_schema = 'public'
  and table_name = 'product_images'
order by ordinal_position;

-- Kiem tra bucket sau khi tao bang Dashboard/API.
select
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
from storage.buckets
where id = 'product-media';

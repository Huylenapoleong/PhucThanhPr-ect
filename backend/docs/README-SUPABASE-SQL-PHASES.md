# README - Phase tao SQL Supabase

Ngay cap nhat: 2026-06-30

File nay dung de ghi nho tien do tao schema Supabase/Postgres. Muc tieu la lan sau mo project len biet dang lam toi dau, da co bang nao, bang nao phu thuoc bang nao, va tiep theo nen tao SQL nao.

## 1. Nguyen tac chung

- Database chay tren Supabase/Postgres.
- Frontend/React khong doc ghi truc tiep cac bang nghiep vu.
- Backend/Spring Boot dung service role de doc ghi database.
- Tat ca bang trong schema `public` deu bat RLS.
- `anon` va `authenticated` bi revoke quyen tren cac bang nghiep vu.
- Tien dung `numeric(18,2)`.
- So luong dung `numeric(14,3)`.
- Thoi gian dung `timestamptz`.
- ID chinh dung `bigint generated always as identity`.
- Khong xoa cung du lieu nghiep vu; dung `status` hoac stage de dong/vo hieu hoa.

## 2. Thu tu chay SQL that

Chay theo thu tu nay trong Supabase Dashboard > SQL Editor:

1. `supabase-customers.sql`
2. `supabase-products.sql`
3. `supabase-leads.sql`
4. `supabase-quotations.sql`
5. `supabase-contracts.sql`
6. `supabase-warranty-repairs.sql`
7. `supabase-customer-reminders.sql`
8. `supabase-call-logs.sql`
9. `supabase-staff-members.sql`
10. `supabase-inventory-movements.sql`
11. `supabase-stock-alerts.sql`
12. `supabase-kpi-snapshots.sql`
13. `supabase-invoices.sql`
14. `supabase-payment-records.sql`
15. `supabase-user-accounts.sql`
16. `supabase-product-images.sql`

Ly do: `supabase-leads.sql` tao ca `leads` va `lead_items`. Trong do:

- `leads.customer_id` tham chieu `customers.id`.
- `lead_items.lead_id` tham chieu `leads.id`.
- `lead_items.product_id` tham chieu `products.id`.

Vi vay `customers` va `products` phai co truoc khi chay file lead. `supabase-quotations.sql` chay sau do vi can FK toi `customers`, `leads`, va `products`. `supabase-contracts.sql` chay sau quotation vi can FK toi `quotations`. `supabase-warranty-repairs.sql` chay sau contracts vi can FK toi `contracts`. `supabase-customer-reminders.sql` chay sau warranty/repairs vi co the FK toi `customer_assets` va `repair_requests`. `supabase-call-logs.sql` chay sau reminders vi cuoc goi AI co the tao lead, tao phieu sua chua, gan hop dong cong no, hoac tao lich callback. `supabase-staff-members.sql` chay sau call logs de tao bang nhan vien va bo sung FK cho cac cot nhan vien da co o cac bang truoc. `supabase-inventory-movements.sql` chay sau staff vi co FK toi `products`, `contracts`, `repair_requests`, `customer_assets`, `customers` va `staff_members`. `supabase-stock-alerts.sql` chay sau inventory movements vi co FK toi `products`, `inventory_movements` va `staff_members`. `supabase-kpi-snapshots.sql` chay sau stock alerts vi tong hop KPI tu sales, hop dong, sua chua, tong dai va ton kho. `supabase-invoices.sql` chay sau staff/contracts/quotations/products de tao day du FK. `supabase-payment-records.sql` chay sau invoices. `supabase-user-accounts.sql` chay sau customers/staff va tao FK toi `auth.users`. `supabase-product-images.sql` chay sau products/staff va tao metadata cho anh nam trong Supabase Storage.

## 3. Thu tu hieu theo nghiep vu

Nghiep vu nen hieu theo flow:

```text
Customer -> Lead -> Lead Items/Product -> Quotation -> Contract -> Invoice -> Payment
```

Giai thich ngan:

- `customers`: ho so nguoi/cong ty.
- `leads`: co hoi ban hang/phieu pipeline cua khach.
- `lead_items`: san pham/nhu cau trong co hoi do.
- `products`: danh muc san pham, gia, ton kho.

## 4. Phase 01 - Customers

File: `supabase-customers.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu ho so khach hang ca nhan/doanh nghiep.
- Luu thong tin phap ly: ten cong ty, MST, dai dien phap luat, dia chi.
- Luu lien he chinh: ten, so dien thoai, email.
- Luu snapshot ket qua tra cuu MST.
- Luu ID doi chieu voi Odoo/MISA.

Bang chinh:

- `public.customers`

Cot quan trong:

- `customer_code`: ma KH, vi du `KH-000001`.
- `customer_type`: `individual` hoac `business`.
- `display_name`: ten hien thi trong CRM.
- `tax_code`: ma so thue, unique khi co gia tri.
- `primary_phone`, `primary_email`: lien he chinh.
- `source`: nguon KH, vi du `web`, `facebook`, `zalo`, `call`, `dauthau`.
- `tax_verification_status`: trang thai xac minh MST.
- `status`: `active`, `inactive`, `blocked`.

Bao mat:

- Da bat RLS.
- Da revoke `anon`, `authenticated`.
- Chi grant `service_role` select/insert/update.

## 5. Phase 02 - Products

File: `supabase-products.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu danh muc san pham.
- Luu gia von, gia ban.
- Luu ton kho hien tai.
- Luu thong so ky thuat linh hoat bang JSON.
- Tu dong suy ra trang thai ton kho.

Bang chinh:

- `public.products`

Cot quan trong:

- `sku`: ma san pham, vi du `SP-000001`.
- `name`: ten san pham.
- `category`: `audio`, `tv`, `led`, `av`, `elv`, `ict`, `accessory`.
- `cost_price`: gia von.
- `sale_price`: gia ban.
- `unit`: `piece`, `set`, `box`, `meter`.
- `stock_quantity`: so luong ton.
- `minimum_stock`: ton toi thieu.
- `stock_status`: generated column, tu suy ra `in_stock`, `low_stock`, `out_of_stock`, `unavailable`.
- `status`: `active`, `inactive`, `discontinued`.

Bao mat:

- Da bat RLS.
- Da revoke `anon`, `authenticated`.
- Chi grant `service_role` select/insert/update.
- Khong grant delete; san pham ngung ban thi doi `status`.

## 6. Phase 03 - Leads va Lead Items

File: `supabase-leads.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu co hoi ban hang/pipeline.
- Ho tro lead tu Web, FB, Zalo, Call, DauThau.
- Ho tro gio hang bo do sau khi qua han checkout.
- Luu snapshot san pham trong lead de khong bi thay doi theo gia/san pham moi.

Bang chinh:

- `public.leads`
- `public.lead_items`

### 6.1. `leads`

Y nghia:

- Mot lead la mot co hoi ban hang.
- Mot customer co the co nhieu lead.
- `customer_id` nullable de van bat duoc lead khi chua co ho so KH day du.

Cot quan trong:

- `lead_code`: ma lead, vi du `LD-000001`.
- `customer_id`: FK den `customers.id`, cho phep null.
- `name`: ten co hoi/du an.
- `source`: `web`, `web_cart`, `facebook`, `zalo`, `call`, `dauthau`, `referral`, `manual`, `other`.
- `contact_name`, `contact_phone`, `contact_email`: snapshot lien he tai thoi diem tao lead.
- `requirement`: noi dung nhu cau.
- `score`: diem lead tu 0 den 100.
- `temperature`: `hot`, `warm`, `cold`.
- `stage`: stage Kanban/pipeline.
- `estimated_value`: gia tri du kien.
- `next_follow_up_at`: lich nhac sales cham soc.
- `origin_ref_type`: `cart`, `call`, `message`, `tender`, `manual`, `other`.
- `origin_ref_id`: ID nguon ngoai, vi du cart id/session id.
- `origin_payload`: snapshot JSON tu nguon tao lead.

Stage hien tai:

```text
new -> contacted -> consulting -> quoted -> negotiating -> won -> delivering -> collecting -> closed
lost
```

Ghi chu:

- `lost` la nhanh mat lead.
- Khi `stage = 'lost'` moi nen co `lost_reason`.
- Khi lead da chot va giao/thu tien xong thi di den `closed`.

### 6.2. `lead_items`

Y nghia:

- Luu san pham/nhu cau trong tung lead.
- Dung cho gio hang bo do va yeu cau bao gia.
- `product_id` nullable de van luu duoc nhu cau chua match voi san pham trong catalog.

Cot quan trong:

- `lead_id`: FK den `leads.id`.
- `product_id`: FK den `products.id`, cho phep null.
- `product_sku`: snapshot SKU.
- `product_name`: snapshot ten san pham.
- `unit`: don vi tinh.
- `quantity`: so luong.
- `unit_price`: don gia tai thoi diem tao lead.
- `discount_amount`: giam gia.
- `line_total`: generated column = `quantity * unit_price - discount_amount`.

## 7. Flow gio hang bo do

Flow de tu dong tao lead tu gio hang:

```text
Khach dang nhap
  -> them san pham vao gio hang
  -> qua han checkout, vi du 30-60 phut
  -> backend kiem tra gio hang chua thanh toan
  -> tao hoac update lead
  -> tao lead_items tu cart items
  -> Telegram bao sales/CEO
  -> Sales goi hoac Zalo cham soc
```

Gia tri goi y khi tao lead:

- `source = 'web_cart'`
- `origin_ref_type = 'cart'`
- `origin_ref_id = cart_id` hoac checkout session id
- `stage = 'new'`
- `temperature = 'warm'` neu co SDT va gio hang co gia tri tot
- `estimated_value = tong gia tri gio hang`

Khong nen tao trung lead neu cung mot gio hang. File SQL da co unique partial index tren:

```text
origin_ref_type + origin_ref_id
```

## 8. Quan he hien tai

```text
customers 1 - n leads
leads 1 - n lead_items
products 1 - n lead_items
```

Doc theo nghiep vu:

```text
Khach hang -> Co hoi ban hang -> San pham trong co hoi
```

Doc theo database:

```text
customers.id -> leads.customer_id
leads.id -> lead_items.lead_id
products.id -> lead_items.product_id
```

## 9. Viec da xong

- Da co SQL tao `customers`.
- Da co SQL tao `products`.
- Da co SQL tao `leads`.
- Da co SQL tao `lead_items`.
- Da co SQL tao `quotations`.
- Da co SQL tao `quotation_items`.
- Da co SQL tao `contracts`.
- Da co SQL tao `customer_assets`.
- Da co SQL tao `repair_requests`.
- Da co SQL tao `customer_reminders`.
- Da co SQL tao `call_logs`.
- Da co SQL tao `staff_members`.
- Da co FK tu cac cot nhan vien ve `staff_members`.
- Da co SQL tao `inventory_movements`.
- Da co SQL tao `stock_alerts`.
- Da co SQL tao `kpi_snapshots`.
- Da co index cho cac cot tim kiem/loc chinh.
- Da co trigger cap nhat `updated_at`.
- Da bat RLS cho cac bang.
- Da revoke quyen browser truc tiep.
- Da grant quyen cho `service_role`.

## 10. Phase 04 - Quotations va Quotation Items

File: `supabase-quotations.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu bao gia chinh thuc gui cho khach.
- Lien ket bao gia voi customer va lead.
- Luu tung dong san pham/dich vu trong bao gia.
- Luu link PDF/Google Docs sau khi xuat bao gia.
- Snapshot ten san pham, don vi, gia ban, gia von tai thoi diem bao gia.

Bang chinh:

- `public.quotations`
- `public.quotation_items`

### 10.1. `quotations`

Y nghia:

- La phan dau cua bao gia.
- Bat buoc co `customer_id`.
- `lead_id` nullable de cho phep tao bao gia truc tiep khong qua pipeline.

Cot quan trong:

- `quotation_code`: ma bao gia, vi du `BG-000001`.
- `customer_id`: FK den `customers.id`.
- `lead_id`: FK den `leads.id`, nullable.
- `quotation_date`: ngay bao gia.
- `valid_until`: ngay het han bao gia.
- `subtotal`: tong tien hang truoc giam gia va thue.
- `discount_amount`: giam gia cap bao gia.
- `tax_amount`: tong thue cap bao gia.
- `total_amount`: generated column = `subtotal - discount_amount + tax_amount`.
- `status`: `draft`, `sent`, `approved`, `rejected`, `expired`.
- `pdf_url`, `google_doc_url`: link file bao gia.
- `payment_terms`, `delivery_terms`, `warranty_terms`: dieu khoan bao gia.

### 10.2. `quotation_items`

Y nghia:

- Moi record la mot dong san pham/dich vu trong bao gia.
- `product_id` nullable de bao gia duoc hang/dich vu chua co trong catalog.
- Khong xoa product da tung nam trong bao gia; chi luu snapshot.

Cot quan trong:

- `quotation_id`: FK den `quotations.id`, on delete cascade.
- `product_id`: FK den `products.id`, nullable.
- `product_sku`, `product_name`: snapshot ma va ten san pham.
- `quantity`, `unit_price`, `cost_price`, `discount_amount`, `tax_rate`.
- `line_subtotal`, `tax_amount`, `line_total`: generated columns.

Quan he:

```text
customers 1 - n quotations
leads 1 - n quotations
quotations 1 - n quotation_items
products 1 - n quotation_items
```

## 11. Phase 05 - Contracts

File: `supabase-contracts.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu hop dong sau khi bao gia da duoc khach chot/duyet.
- Lien ket hop dong voi customer va quotation.
- Luu gia tri hop dong, so tien da thanh toan, con phai thu.
- Luu trang thai hop dong va trang thai thanh toan.
- Luu link Google Docs/PDF/Drive cua hop dong.
- Luu ma doi chieu MISA de phuc vu cong no/ke toan.

Bang chinh:

- `public.contracts`

Cot quan trong:

- `contract_code`: ma hop dong, vi du `HD-000001`.
- `customer_id`: FK den `customers.id`.
- `quotation_id`: FK den `quotations.id`, nullable nhung unique.
- `signed_date`, `start_date`, `end_date`, `payment_due_date`.
- `total_value`: tong gia tri hop dong.
- `paid_amount`: so tien da thanh toan.
- `remaining_amount`: generated column = `total_value - paid_amount`.
- `payment_status`: `unpaid`, `partial`, `paid`, `overdue`.
- `status`: `draft`, `active`, `completed`, `cancelled`.
- `document_url`, `google_doc_url`, `pdf_url`: link file hop dong.
- `misa_reference`: ma chung tu/hoa don/cong no tren MISA.

Quan he:

```text
customers 1 - n contracts
quotations 1 - 0..1 contracts
```

Ghi chu:

- MVP khong tao `contract_items`; hang hoa cua hop dong lay tu `quotation_items` cua bao gia nguon.
- Sau nay neu hop dong co noi dung khac bao gia thi them `contract_items`.

## 12. Phase 06 - Warranty & Repairs

File: `supabase-warranty-repairs.sql`

Trang thai: da tao SQL.

Muc dich:

- Chuan hoa nghiep vu Bao hanh & Sua chua tren web.
- Luu thiet bi/serial da ban giao cho khach.
- Tiep nhan yeu cau BH/SC tu Web/Zalo/Facebook/Call.
- Gan KTV phu trach bang `technician_user_id`.
- Theo doi trang thai xu ly tu luc tiep nhan den khi thong bao khach va tra hang.
- Luu chi phi sua chua, ket qua xu ly, kenh thong bao khach.

Bang chinh:

- `public.customer_assets`
- `public.repair_requests`

### 12.1. `customer_assets`

Y nghia:

- Moi record la mot thiet bi/serial da ban giao cho khach.
- Day la nen de biet thiet bi con bao hanh hay khong.
- Co the tao tu hop dong sau khi giao hang.

Cot quan trong:

- `asset_code`: ma thiet bi, vi du `TB-000001`.
- `customer_id`: FK den `customers.id`.
- `product_id`: FK den `products.id`.
- `contract_id`: FK den `contracts.id`, nullable.
- `product_sku`, `product_name`: snapshot san pham.
- `serial_number`: serial thiet bi, unique khi co gia tri.
- `purchase_date`, `delivered_at`.
- `warranty_months`, `warranty_starts_on`, `warranty_expires_on`.
- `status`: `active`, `expired`, `returned`, `retired`.

### 12.2. `repair_requests`

Y nghia:

- Moi record la mot phieu bao hanh/sua chua.
- Co the gan voi `customer_asset_id` neu da xac dinh thiet bi.
- Van tiep nhan duoc yeu cau khi khach chua co asset trong he thong bang `reported_product_name`.

Workflow web:

```text
received -> assigned -> processing -> waiting_parts/waiting_customer -> completed -> notified -> returned
cancelled
```

Cot quan trong:

- `repair_code`: ma phieu, vi du `SC-000001`.
- `customer_id`: FK den `customers.id`.
- `customer_asset_id`: FK den `customer_assets.id`, nullable.
- `product_id`: FK den `products.id`, nullable.
- `request_type`: `warranty`, `paid_repair`, `outsourced`.
- `request_channel`: `web`, `zalo`, `facebook`, `call`, `manual`, `other`.
- `priority`: `low`, `normal`, `high`, `urgent`.
- `issue_description`: mo ta loi, bat buoc.
- `warranty_decision`: `pending`, `approved`, `rejected`, `not_applicable`.
- `technician_user_id`: FK den `staff_members.id`, KTV phu trach.
- `status`: trang thai workflow.
- `parts_cost`, `labor_cost`, `outsourced_cost`, `total_cost`.
- `resolution`: noi dung xu ly.
- `notification_channel`, `notification_status`, `customer_notified_at`.

Quan he:

```text
customers 1 - n customer_assets
products 1 - n customer_assets
contracts 1 - n customer_assets
customers 1 - n repair_requests
customer_assets 1 - n repair_requests
products 1 - n repair_requests
```

## 13. Phase 07 - Customer Reminders

File: `supabase-customer-reminders.sql`

Trang thai: da tao SQL.

Muc dich:

- Chuan hoa CSKH tu dong tren web.
- Tao lich nhac no, nhac thanh toan, nhac bao hanh, sinh nhat, follow-up.
- Tao thong bao khach sau khi phieu sua chua hoan thanh.
- Gan lich nhac voi customer, lead, contract, thiet bi hoac phieu sua chua.
- Theo doi kenh gui, trang thai gui, retry khi gui loi.

Bang chinh:

- `public.customer_reminders`

Cot quan trong:

- `reminder_code`: ma lich nhac, vi du `NH-000001`.
- `customer_id`: FK den `customers.id`.
- `lead_id`: FK den `leads.id`, nullable.
- `contract_id`: FK den `contracts.id`, nullable.
- `customer_asset_id`: FK den `customer_assets.id`, nullable.
- `repair_request_id`: FK den `repair_requests.id`, nullable.
- `reminder_type`: `debt`, `warranty`, `event`, `birthday`, `follow_up`, `repair_completed`, `contract_renewal`, `payment_due`.
- `due_at`: thoi diem can nhac/gui.
- `assigned_to_user_id`: FK den `staff_members.id`, nhan vien phu trach.
- `channel`: `telegram`, `zalo`, `facebook`, `email`, `call`, `sms`, `manual`.
- `status`: `pending`, `sent`, `done`, `cancelled`, `failed`, `snoozed`.
- `next_retry_at`, `retry_count`: dung cho retry khi gui that bai.
- `external_message_id`: ID tin nhan tu nha cung cap neu co.

Quan he:

```text
customers 1 - n customer_reminders
leads 1 - n customer_reminders
contracts 1 - n customer_reminders
customer_assets 1 - n customer_reminders
repair_requests 1 - n customer_reminders
```

## 14. Phase 08 - AI Call Center / Call Logs

File: `supabase-call-logs.sql`

Trang thai: da tao SQL.

Muc dich:

- Chuan hoa nghiep vu tong dai AI 24/7.
- Luu cuoc goi tu nut goi tren web, hotline, Zalo/Facebook hoac nhap tay.
- Ho tro ca IVR bam phim va AI voice agent noi tu nhien.
- Luu transcript, intent AI nhan dien, tom tat nhu cau, recording URL.
- Chuyen dung bo phan khi can nguoi that: sales, bao hanh/sua chua, ke toan/cong no, operator.
- Gan cuoc goi voi lead, phieu sua chua, hop dong hoac lich nhac/callback.

Bang chinh:

- `public.call_logs`

Cot quan trong:

- `call_code`: ma cuoc goi, vi du `CG-000001`.
- `external_call_id`: ID cuoc goi tu nha cung cap tong dai/AI voice.
- `call_provider`: nha cung cap, vi du `twilio`, `stringee`, `omicall`, `cloudfone`, `viettel`, `fpt_ai`.
- `source_channel`: `web_call`, `phone`, `zalo`, `facebook`, `manual`, `other`.
- `customer_id`: FK den `customers.id`, nullable.
- `lead_id`: FK den `leads.id`, nullable.
- `repair_request_id`: FK den `repair_requests.id`, nullable.
- `contract_id`: FK den `contracts.id`, nullable.
- `customer_reminder_id`: FK den `customer_reminders.id`, nullable.
- `phone_number`, `caller_name`: snapshot nguoi goi.
- `direction`: `inbound` hoac `outbound`.
- `interaction_mode`: `ivr`, `voice_agent`, `ivr_voice`, `human`, `manual`.
- `ivr_digit`: phim khach bam.
- `route`: nhanh nghiep vu AI/IVR phan loai.
- `intent`, `intent_confidence`: nhu cau AI nhan dien va do tin cay.
- `customer_requirement`, `transcript`, `ai_summary`, `ai_resolution`.
- `result`: ket qua cuoc goi.
- `handoff_required`, `transfer_reason`, `transferred_to_user_id`: thong tin chuyen nguoi that.
- `recording_url`, `external_payload`: link ghi am va payload nha cung cap.

Flow goi den de xuat:

```text
Khach bam nut goi tren web / goi hotline
  -> Tong dai chao
  -> Khach bam phim hoac noi tu nhien
  -> Phim 1 / intent bao_gia: route = sales_quote, co the tao lead
  -> Phim 2 / intent bao_hanh: route = warranty_repair, co the tao repair_request
  -> Phim 3 / intent cong_no: route = accounting_debt, gan contract/reminder neu co
  -> Phim 0 / intent gap_nhan_vien: route = operator, handoff_required = true
  -> Ngoai gio: AI ghi nhan nhu cau, tao lead/repair_request/customer_reminder
  -> Luu call_logs lam lich su va doi soat
```

Gia tri goi y:

- Khach can bao gia: `route = 'sales_quote'`, `result = 'lead_created'`, co `lead_id`.
- Khach bao hong thiet bi: `route = 'warranty_repair'`, `result = 'repair_created'`, co `repair_request_id`.
- Khach can goi lai: `result = 'reminder_created'`, co `customer_reminder_id`.
- Khach can nguoi that: `handoff_required = true`, `result = 'transferred'` hoac `callback_required`.

Quan he:

```text
customers 1 - n call_logs
leads 1 - n call_logs
repair_requests 1 - n call_logs
contracts 1 - n call_logs
customer_reminders 1 - n call_logs
```

## 15. Phase 09 - Staff Members

File: `supabase-staff-members.sql`

Trang thai: da tao SQL.

Muc dich:

- Quan ly nhan su noi bo: CEO, admin, sales, KTV, ke toan, kho, support, tong dai.
- Lam bang goc cho cac cot `assigned_to_user_id`, `created_by_user_id`, `technician_user_id`, `handled_by_user_id`.
- Luu kenh thong bao noi bo nhu Telegram chat ID, Zalo user ID, Facebook user ID.
- Gan nhan vien voi Supabase Auth bang `auth_user_id` neu sau nay co dang nhap.
- Khong xoa nhan vien cu; doi `status` de giu lich su nghiep vu.

Bang chinh:

- `public.staff_members`

Cot quan trong:

- `id`: UUID nhan vien, duoc cac bang nghiep vu tham chieu.
- `staff_code`: ma nhan vien, vi du `NV-000001`.
- `full_name`, `display_name`, `phone`, `email`.
- `role`: `ceo`, `admin`, `manager`, `sales`, `technician`, `accountant`, `warehouse`, `support`, `operator`, `other`.
- `department`: `executive`, `sales`, `technical`, `accounting`, `warehouse`, `support`, `operations`, `other`.
- `auth_user_id`: UUID tai khoan Supabase Auth neu co.
- `telegram_chat_id`, `zalo_user_id`, `facebook_user_id`: dung gui thong bao noi bo.
- `status`: `active`, `inactive`, `resigned`, `blocked`.

FK duoc bo sung:

- `leads.assigned_to_user_id` -> `staff_members.id`
- `quotations.created_by_user_id` -> `staff_members.id`
- `contracts.created_by_user_id` -> `staff_members.id`
- `repair_requests.technician_user_id` -> `staff_members.id`
- `customer_reminders.assigned_to_user_id` -> `staff_members.id`
- `call_logs.transferred_to_user_id` -> `staff_members.id`
- `call_logs.handled_by_user_id` -> `staff_members.id`

Quan he:

```text
staff_members 1 - n leads
staff_members 1 - n quotations
staff_members 1 - n contracts
staff_members 1 - n repair_requests
staff_members 1 - n customer_reminders
staff_members 1 - n call_logs
```

## 16. Phase 10 - Inventory Movements

File: `supabase-inventory-movements.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu lich su nhap, xuat, tra hang, dieu chinh, kiem ke va chuyen kho.
- Lam nguon doi soat cho `products.stock_quantity`.
- Biet moi movement phat sinh tu hop dong, phieu sua chua, thiet bi/serial, customer hay chung tu ngoai.
- Biet nhan vien nao thuc hien va nhan vien nao duyet.
- Giu `stock_before`, `stock_after`, `quantity_delta` de truy vet sai lech ton kho.

Bang chinh:

- `public.inventory_movements`

Cot quan trong:

- `movement_code`: ma phieu kho, vi du `KHO-000001`.
- `product_id`: FK den `products.id`.
- `contract_id`: FK den `contracts.id`, nullable.
- `repair_request_id`: FK den `repair_requests.id`, nullable.
- `customer_asset_id`: FK den `customer_assets.id`, nullable.
- `customer_id`: FK den `customers.id`, nullable.
- `performed_by_user_id`, `approved_by_user_id`: FK den `staff_members.id`.
- `warehouse_code`: ma kho, mac dinh `MAIN`.
- `movement_type`: `purchase_in`, `sale_out`, `repair_out`, `repair_return_in`, `stock_adjustment_in`, `stock_adjustment_out`, ...
- `movement_direction`: `in` hoac `out`.
- `quantity`: so luong duong.
- `quantity_delta`: generated column, nhap la duong, xuat la am.
- `stock_before`, `stock_after`: ton truoc/sau movement.
- `unit_cost`, `unit_price`, `cost_amount`, `sale_amount`.
- `source_type`, `source_ref`, `document_number`: doi soat MISA/Google Sheet/phieu kho/chung tu ngoai.
- `serial_number`, `batch_number`: doi soat thiet bi/lo hang neu can.
- `reversal_of_movement_id`: movement dao chieu khi sua sai.

Quy tac cap nhat ton:

```text
Backend/Spring Boot tao inventory_movements
  -> tinh stock_before tu products.stock_quantity
  -> tinh stock_after = stock_before +/- quantity
  -> update products.stock_quantity = stock_after
  -> commit chung 1 transaction
```

Ghi chu:

- Khong nen xoa movement kho.
- Neu ghi sai, tao movement dao chieu va gan `reversal_of_movement_id`.
- File SQL chua tao trigger tu dong cong/tru `products.stock_quantity`; logic nay de backend xu ly trong transaction de de doi soat.

Quan he:

```text
products 1 - n inventory_movements
contracts 1 - n inventory_movements
repair_requests 1 - n inventory_movements
customer_assets 1 - n inventory_movements
customers 1 - n inventory_movements
staff_members 1 - n inventory_movements
```

## 17. Phase 11 - Stock Alerts

File: `supabase-stock-alerts.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu lich su canh bao ton kho thap, het hang, can dat lai hang.
- Ghi nhan da gui Telegram/Zalo/email/SMS hay chua.
- Theo doi ai phu trach, ai da xac nhan, ai da xu ly xong.
- Tranh tao trung canh bao dang mo cho cung san pham/kho/loai canh bao.
- Luu snapshot SKU, ten san pham, ton hien tai va ton toi thieu tai thoi diem canh bao.

Bang chinh:

- `public.stock_alerts`

Cot quan trong:

- `alert_code`: ma canh bao, vi du `TK-000001`.
- `product_id`: FK den `products.id`.
- `inventory_movement_id`: FK den `inventory_movements.id`, nullable.
- `assigned_to_user_id`: FK den `staff_members.id`, nhan vien/bo phan phu trach.
- `acknowledged_by_user_id`, `resolved_by_user_id`: FK den `staff_members.id`.
- `warehouse_code`: ma kho, mac dinh `MAIN`.
- `product_sku`, `product_name`, `unit`: snapshot san pham.
- `alert_type`: `low_stock`, `out_of_stock`, `reorder`, `manual`.
- `severity`: `low`, `normal`, `high`, `critical`.
- `stock_quantity`, `minimum_stock`, `shortage_quantity`.
- `notification_channel`: `telegram`, `zalo`, `email`, `sms`, `manual`, `none`.
- `status`: `open`, `sent`, `acknowledged`, `resolved`, `cancelled`, `failed`, `snoozed`.
- `sent_at`, `acknowledged_at`, `resolved_at`, `next_retry_at`, `retry_count`.
- `external_message_id`: ID tin nhan tu nha cung cap neu co.

Flow canh bao de xuat:

```text
Cron/Spring Boot quet products
  -> thay stock_quantity <= minimum_stock
  -> tao stock_alerts neu chua co canh bao dang mo
  -> gui Telegram/Zalo/email cho kho/CEO
  -> cap nhat sent_at/status
  -> nhan vien acknowledged
  -> nhap hang/bo sung ton
  -> resolved khi stock_quantity > minimum_stock hoac da xu ly
```

Quan he:

```text
products 1 - n stock_alerts
inventory_movements 1 - n stock_alerts
staff_members 1 - n stock_alerts
```

## 18. Phase 12 - KPI Snapshots

File: `supabase-kpi-snapshots.sql`

Trang thai: da tao SQL.

Muc dich:

- Luu ket qua KPI da tinh theo ngay/tuan/thang/quy/nam.
- Phuc vu dashboard CEO va bao cao Telegram dinh ky.
- Tong hop doanh thu, pipeline, cong no, loi nhuan, lead, bao hanh/sua chua, call center va ton kho.
- Ho tro KPI toan cong ty, theo phong ban hoac theo tung nhan vien.
- Luu `source_payload` de doi soat so lieu nguon tu Supabase/MISA/Google Sheets.

Bang chinh:

- `public.kpi_snapshots`

Cot quan trong:

- `snapshot_code`: ma snapshot, vi du `KPI-2026-06-COMPANY`.
- `period_type`: `day`, `week`, `month`, `quarter`, `year`, `custom`.
- `period_start`, `period_end`: khoang thoi gian bao cao.
- `scope_type`: `company`, `department`, `staff`.
- `department`: phong ban neu `scope_type = department`.
- `staff_member_id`: FK den `staff_members.id` neu `scope_type = staff`.
- `revenue`, `pipeline_value`, `quotation_value`, `contract_value`.
- `receivables`, `overdue_amount`, `collected_amount`, `profit`, `gross_margin_rate`.
- `new_leads`, `won_leads`, `lost_leads`, `open_leads`, `quoted_leads`, `conversion_rate`, `avg_deal_value`.
- `completed_repairs`, `open_repairs`, `overdue_repairs`.
- `call_count`, `ai_resolved_calls`, `transferred_calls`.
- `low_stock_products`, `out_of_stock_products`, `active_stock_alerts`.
- `generated_at`, `generated_by_user_id`.
- `report_status`: `generated`, `sent`, `failed`, `superseded`.
- `notification_channel`, `sent_at`, `external_message_id`.
- `source_payload`: snapshot JSON cac so lieu nguon.

Flow tinh KPI de xuat:

```text
Cron/Spring Boot hang ngay/tuan/thang
  -> query leads, quotations, contracts, repair_requests, call_logs, products, stock_alerts
  -> lay them cong no/loi nhuan tu MISA neu co
  -> tinh KPI
  -> upsert kpi_snapshots theo period + scope
  -> gui Telegram CEO
  -> cap nhat report_status/sent_at/external_message_id
```

Ghi chu:

- `kpi_snapshots` la bang tong hop, khong phai du lieu giao dich goc.
- Neu can sua so lieu da gui, nen tao snapshot moi va danh dau snapshot cu `superseded`.
- Unique index ngan tao trung snapshot dang hien hanh cho cung ky va cung scope.

Quan he:

```text
staff_members 1 - n kpi_snapshots
```

## 19. Phase 13 - Invoices va Invoice Items

File: `supabase-invoices.sql`

Trang thai: da tao SQL; nguoi dung xac nhan `invoices` va `invoice_items` da ton tai tren Supabase DB ngay 2026-06-30.

Muc dich:

- Tach hoa don khoi hop dong va bao gia.
- Mot hop dong co the co nhieu hoa don.
- Ban le co the co hoa don ma khong co hop dong.
- Luu snapshot nguoi mua va dong hang tai thoi diem phat hanh.
- Luu ket qua phat hanh tu MISA/nha cung cap: so hoa don, ky hieu, ma co quan thue, XML/PDF.
- Ho tro hoa don ban hang, dieu chinh va thay the.
- Theo doi rieng `invoice_status` va `payment_status`.

Bang chinh:

- `public.invoices`
- `public.invoice_items`

Quan he:

```text
customers 1 - n invoices
contracts 1 - n invoices
quotations 1 - n invoices
invoices 1 - n invoice_items
products 1 - n invoice_items
quotation_items 1 - n invoice_items
invoices 1 - n adjustment/replacement invoices
invoices 1 - n customer_reminders
staff_members 1 - n invoices
```

Ghi chu quan trong:

- `invoice_code` la ma noi bo; `invoice_number` la so chinh thuc do provider tra ve.
- `buyer_*` la snapshot, khong doc dong tu customer sau khi hoa don da phat hanh.
- `paid_amount` la gia tri tong hop de doc nhanh, khong phai so giao dich goc.
- File bo sung `customer_reminders.invoice_id` de nhac cong no cho ca hoa don ban le.
- Hoa don thue chinh thuc do MISA/nha cung cap phat hanh; database nay luu workflow va ket qua doi soat.
- Hoa don da phat hanh khong xoa; dung adjustment/replacement/cancelled theo nghiep vu ke toan.

## 20. Phase 14 - Payment Records

File: `supabase-payment-records.sql`

Trang thai: da tao SQL; nguoi dung xac nhan `payment_records` da ton tai tren Supabase DB ngay 2026-06-30.

Muc dich:

- Luu tung lan thu tien va hoan tien.
- Ho tro thanh toan nhieu dot cho mot hoa don/hop dong.
- Chong xu ly trung webhook bang `idempotency_key`.
- Doi soat giao dich ngan hang, payment gateway, MISA/Odoo va import.
- Giu refund thanh record rieng, tham chieu giao dich thu tien goc.

Bang chinh:

- `public.payment_records`

Quan he:

```text
customers 1 - n payment_records
contracts 1 - n payment_records
invoices 1 - n payment_records
payment_records 1 - n refund records
staff_members 1 - n payment_records
```

Quy tac backend:

```text
Payment chuyen sang confirmed
  -> khoa invoice/contract lien quan
  -> tinh tong signed_amount cua payment_records confirmed
  -> cap nhat invoices.paid_amount va payment_status
  -> cap nhat contracts.paid_amount va payment_status
  -> ghi outbox event payment.recorded
  -> commit cung mot transaction
```

File SQL khong tao trigger tu dong sua cong no. Logic tai chinh de Billing Service xu ly trong transaction de de test, doi soat va tranh trigger ngam.

## 21. Phase 15 - User Accounts va Authorization

File: `supabase-user-accounts.sql`

Trang thai: da tao SQL, chua xac nhan chay tren Supabase DB.

Muc dich:

- Dung Supabase Auth de quan ly password, session, OTP va MFA.
- Tao ho so tai khoan nghiep vu 1-1 voi `auth.users`.
- Cho phep mot customer/doanh nghiep co nhieu tai khoan thanh vien.
- Gan vai tro customer: owner, buyer, accounting, viewer.
- Giu audit login/logout/password/MFA/invite/lock o dang append-only.
- Bo sung FK `staff_members.auth_user_id -> auth.users.id`.

Bang chinh:

- `public.user_accounts`
- `public.customer_memberships`
- `public.auth_audit_logs`

Quan he:

```text
auth.users 1 - 1 user_accounts
user_accounts 1 - n customer_memberships
customers 1 - n customer_memberships
auth.users 1 - 0..1 staff_members
auth.users 1 - n auth_audit_logs
```

Quy tac:

- Khong luu password, refresh token, OTP hoac MFA secret trong `public`.
- Khong tao trigger tren `auth.users`; User/CRM Service tao profile va retry neu can.
- Xoa Auth user se cascade `user_accounts`/memberships, nhung chi set null `staff_members.auth_user_id` de giu lich su nhan vien.
- `auth_audit_logs` chi grant select/insert cho backend, khong update/delete.

## 22. Phase 16 - Product Images va Supabase Storage

File: `supabase-product-images.sql`

Trang thai: da tao SQL, chua xac nhan chay tren Supabase DB.

Muc dich:

- Tao `public.product_images` de mot san pham co nhieu anh.
- File nhi phan luu trong Supabase Storage; database chi luu metadata va object key.
- Mot san pham chi co toi da mot anh primary dang `active`.
- Khong luu public URL co dinh; backend tao URL tu `bucket_name` va `object_key`.
- Ho tro doi tu Supabase Storage sang S3-compatible provider bang adapter.

Cau hinh bucket `product-media` bang Supabase Dashboard hoac Storage API:

```text
Public bucket: true
File size limit: 10 MB
Allowed MIME: image/jpeg, image/png, image/webp, image/avif
Object key: products/{product_id}/{uuid}.{ext}
```

Quy tac backend:

```text
Nhan file
  -> kiem tra MIME, dung luong va noi dung anh
  -> upload bang Storage API/S3 API
  -> bat dau database transaction
  -> neu la anh primary: demote anh primary cu
  -> insert product_images
  -> commit
  -> neu database loi: xoa object vua upload
  -> khi xoa: chuyen status=archived
  -> worker xoa object vat ly sau thoi gian luu tru
```

- Frontend doc danh sach anh qua Catalog/Inventory Service.
- File public duoc phuc vu qua CDN, nhung upload/delete chi do backend thuc hien.
- Khong sua truc tiep `storage.buckets` hoac `storage.objects` bang SQL.
- `products.image_url` la cot legacy; anh moi dung `product_images`.

## 23. Viec tiep theo

Phase tiep theo nen lam:

Core SQL cho 8 nghiep vu ban dau da co du bang nen.

Bang nen co the them sau MVP:

1. `supabase-outbox-events.sql`
   - Publish event Kafka an toan cung transaction nghiep vu.

2. `supabase-message-logs.sql`
   - Luu lich su tin nhan that su da gui/nhan qua Zalo, Facebook, Telegram, email, SMS.

3. `supabase-generated-documents.sql`
   - Quan ly tap trung Google Docs/PDF cua bao gia, hop dong, phieu bao hanh.

4. `supabase-deliveries.sql`
   - Phieu giao hang/ban giao thiet bi neu can tach khoi hop dong.

## 24. Cach dat ten file tiep theo

Dung quy uoc:

```text
supabase-<ten-nghiep-vu>.sql
```

Vi du:

```text
supabase-quotations.sql
supabase-contracts.sql
supabase-repairs.sql
supabase-kpi.sql
```

## 25. Ghi chu ngan cho lan sau

- Kien truc tong the, cong nghe thay the va 8 flow nghiep vu: xem `README-ARCHITECTURE.md`.
- Da chot service-based architecture: client -> API Gateway -> cac Spring Boot service.
- Da gom service theo business capability: User/CRM, Catalog/Inventory, Commerce, After-sales, Automation, Communication va Integration/Reporting.
- Commerce so huu tron flow Lead -> Quotation -> Contract -> Invoice -> Payment; khong tach service theo tung bang.
- Da chot Supabase PostgreSQL la source of truth; Airtable va Google Sheets khong con la database nghiep vu.
- Da chot Redis cho CQRS/read cache, Kafka cho async event, Transactional Outbox de publish an toan.
- Phase dau chua dung Debezium; reminder theo thoi gian dung Spring Scheduler/Quartz.
- Backend Phase B da scaffold Maven multi-module, shared-kernel, Gateway, cac
  business service va Document Worker. Xem `README-BACKEND-PHASE-B-PROGRESS.md`.
- Login da co file SQL `supabase-user-accounts.sql`, nhung chua xac nhan chay tren Supabase; password do Supabase Auth quan ly.
- Neu dang lam Lead/Kanban: xem `supabase-leads.sql`.
- Neu dang lam Bao gia: xem `supabase-quotations.sql`.
- Neu dang lam Hop dong: xem `supabase-contracts.sql`.
- Neu dang lam Hoa don: xem `supabase-invoices.sql`.
- Neu dang lam Thanh toan/Cong no: xem `supabase-payment-records.sql`.
- Neu dang lam Bao hanh/Sua chua: xem `supabase-warranty-repairs.sql`.
- Neu dang lam CSKH/Nhac lich: xem `supabase-customer-reminders.sql`.
- Neu dang lam AI Call Center: xem `supabase-call-logs.sql`.
- Neu dang lam nhan su/quyen phu trach: xem `supabase-staff-members.sql`.
- Neu dang lam nhap/xuat ton kho: xem `supabase-inventory-movements.sql`.
- Neu dang lam canh bao ton kho: xem `supabase-stock-alerts.sql`.
- Neu dang lam KPI/Dashboard CEO: xem `supabase-kpi-snapshots.sql`.
- Neu dang lam danh muc hang hoa: xem `supabase-products.sql`.
- Neu dang lam anh san pham/Storage: xem `supabase-product-images.sql`.
- Neu dang lam ho so KH/MST/hop dong: xem `supabase-customers.sql`.
- Neu them bang moi trong `public`: nho bat RLS va revoke `anon`, `authenticated`.
- Neu bang moi co FK: nho tao index cho cot FK.
- Neu can browser doc ghi truc tiep bang nao, phai thiet ke RLS policy rieng; hien tai huong dung la browser goi backend.

## 26. Trang thai Supabase DB thuc te

Nguoi dung xac nhan ngay 2026-06-30 Supabase hien co 17 bang, deu dang `0 rows`:

```text
User/CRM:
  customers
  staff_members

Catalog/Inventory:
  products
  inventory_movements

Commerce:
  leads
  lead_items
  quotations
  quotation_items
  contracts
  invoices
  invoice_items
  payment_records

After-sales:
  customer_assets
  repair_requests
  customer_reminders

Communication:
  call_logs

Reporting:
  kpi_snapshots
```

Doi chieu voi phase 01-14:

- Da co 17/18 bang du kien.
- Con thieu `stock_alerts`; can chay `supabase-stock-alerts.sql` sau khi xac nhan schema/RLS hien tai.
- Phase 15 da co file SQL nhung chua xac nhan chay: `user_accounts`, `customer_memberships`, `auth_audit_logs`.
- Phase 16 da co file SQL nhung chua xac nhan chay: `product_images`; bucket `product-media` cung chua xac nhan da tao.
- Sau khi chay `stock_alerts`, phase 15 va phase 16, database du kien co 22 bang public.
- `outbox_events`, `processed_events`, `message_logs`, `generated_documents`, `document_templates`, `deliveries`, `integration_logs` la phase sau, chua tinh la thieu.
- Dashboard hien thi `Disabled` tren cac bang, nhung can query `pg_class.relrowsecurity` va publication `supabase_realtime` de biet do la RLS hay Realtime; khong bat/tat theo suy doan.

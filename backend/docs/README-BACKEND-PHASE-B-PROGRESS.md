# Tien do Phase B - Backend nen

Ngay cap nhat: 2026-07-01

## Muc tieu

Khoi tao service-based backend theo `README-ARCHITECTURE.md`, dung chung Supabase
PostgreSQL trong giai doan dau nhung tach ro quyen so huu bang va tien trinh chay.

## Da hoan thanh

- [x] Chuyen root thanh Maven multi-module parent.
- [x] Pin Java 17, Spring Boot 4.0.7 va Spring Cloud 2025.1.2.
- [x] Tao `shared-kernel` co pham vi hep.
- [x] Tao API Gateway WebFlux va route den cac service.
- [x] Tao User/CRM, Catalog/Inventory, Commerce va After-sales Service.
- [x] Tao Automation, Communication va Integration/Reporting Service.
- [x] Tao Document Worker khong co public business API.
- [x] Tao cau hinh dung chung cho Supabase PostgreSQL, Supabase JWT, Kafka, Redis.
- [x] Bat Actuator health probe va Prometheus cho cac process.
- [x] Tao Docker Compose cho Redis va Kafka local.
- [x] Tao `.env.example`, khong commit secret that.
- [x] Them unit test dam bao role chi doc tu `app_metadata`, khong doc `user_metadata`.
- [x] Tao Transactional Outbox publisher/relay va SQL `outbox_events`.
- [x] Hoan thien vertical slice `auth` trong User/CRM Service.
- [x] Hoan thien vertical slice `product` trong Catalog/Inventory Service.
- [x] Mo public API doc san pham; cac API ghi van bat buoc JWT/role.
- [x] Hoan thien register/login/refresh/logout/me/forgot-password qua Supabase Auth.

## Shared kernel gom gi

```text
shared-kernel
|-- security
|   |-- AuthenticatedUser
|   |-- SupabaseJwtAuthenticationConverter
|   `-- SharedServletSecurityConfiguration
|-- web
|   |-- ApiError
|   |-- BusinessException
|   `-- CorrelationIds
|-- event
|   `-- EventEnvelope
|-- storage
|   |-- ObjectStoragePort
|   `-- StoredObject
`-- resources
    |-- application-shared.yml
    |-- application-postgres.yml
    |-- application-kafka.yml
    `-- application-redis.yml
```

Khong dua entity, repository, DTO nghiep vu hay business service vao
`shared-kernel`.

## Module va cong

| Module | Port | Trang thai nen |
|---|---:|---|
| `api-gateway` | 8080 | Gateway WebFlux, JWT, route, correlation ID |
| `user-crm-service` | 8081 | PostgreSQL, Kafka, JWT |
| `catalog-inventory-service` | 8082 | PostgreSQL, Redis, Kafka, JWT |
| `commerce-service` | 8083 | PostgreSQL, Redis, Kafka, JWT |
| `aftersales-service` | 8084 | PostgreSQL, Kafka, JWT |
| `automation-service` | 8085 | REST tool APIs, Kafka, JWT; khong ket noi DB |
| `communication-service` | 8086 | PostgreSQL, Kafka, mail, JWT |
| `integration-reporting-service` | 8087 | PostgreSQL, Redis, Kafka, Scheduler, JWT |
| `document-worker` | 8088 | Kafka worker; port chi phuc vu Actuator |

## Quyet dinh ky thuat

- API Gateway dung `spring-cloud-starter-gateway-server-webflux`.
- Khong tron Gateway WebMVC voi Spring WebFlux.
- Backend dai han ket noi Supabase bang Direct connection neu ha tang co IPv6.
- Neu server chi co IPv4, dung Supavisor Session pooler cong 5432.
- Khong dung Transaction pooler cong 6543 cho service dai han trong phase nay.
- Hibernate chi `validate`, khong tu tao/sua schema.
- JWT duoc xac minh qua Supabase JWKS.
- Role phan quyen chi doc tu `app_metadata`.
- Automation Service goi internal API, khong duoc ghi truc tiep bang service khac.

## Uu tien hien tai

Hai feature da duoc tach package day du theo huong feature-first:

```text
auth/
|-- client
|-- config
|-- controller
|-- domain
|-- dto
|-- repository
`-- service

product/
|-- controller
|-- domain
|-- dto
|-- repository
`-- service
```

Chi `auth` va `product` duoc xem la lat cat frontend phase 1. Cac domain/service
con lai moi o muc scaffold hoac API ban dau, can tiep tuc refactor theo cung mau
truoc khi xem la hoan chinh.

## Chua lam trong Phase B

- [ ] Hoan thien entity/repository/controller cho cac domain con lai.
- [ ] Redis read model va cache invalidation consumer.
- [ ] Rate limiter Gateway dung Redis.
- [ ] Storage adapter Supabase S3.
- [ ] Document template engine.
- [ ] Channel adapter Telegram/Zalo/Facebook/email.
- [ ] AI provider va Voice/SIP provider.
- [ ] Dockerfile cho tung service va CI/CD.

## Cach build

Chay tu thu muc workspace (thu muc cha cua `backend`):

```powershell
backend\mvnw.cmd -f backend\pom.xml clean verify
```

## Cach chay ha tang local

```powershell
Copy-Item .env.example .env
docker compose up -d redis kafka
```

Sau khi dien dung Supabase connection/JWKS trong `.env`, chay tung service tu
IntelliJ hoac Maven. Khong commit `.env`.

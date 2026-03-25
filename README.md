# Auto — 이커머스 상품 자동화 플랫폼

네이버 스마트스토어, 쿠팡 등 복수의 이커머스 플랫폼에 **엑셀 파일 한 장**으로 상품을 일괄 등록하고, 주문·상품 데이터를 자동 동기화하는 Spring Boot 기반 백오피스 도구입니다.

---

## 주요 기능 (MVP)

| 기능 | 네이버 | 쿠팡 | 11번가 |
|------|:---:|:---:|:---:|
| OAuth 인증 | ✅ | ✅ | 🔜 |
| 엑셀 일괄 등록 | ✅ | ✅ | 🔜 |
| 상품 조회/수정 | ✅ | ✅ | 🔜 |
| 카테고리 자동 매핑 | ✅ | ✅ | 🔜 |
| 상품 동기화 | ✅ | 🔜 | 🔜 |
| 주문 동기화 | ✅ | 🔜 | 🔜 |
| 결과 CSV 내보내기 | ✅ | ✅ | 🔜 |

---

## 기술 스택

- **Language:** Java 21
- **Framework:** Spring Boot 4.0.0
- **Database:** MySQL 8.0 + Spring Data JPA
- **HTTP Client:** Spring WebFlux (WebClient)
- **Excel Parsing:** Apache POI 5.2.5
- **Template Engine:** Thymeleaf
- **API Docs:** SpringDoc OpenAPI (Swagger UI)
- **Build:** Gradle 9.2.1

---

## 아키텍처 개요

```
com.example.auto/
├── controller/          # REST API 엔드포인트
├── domain/              # JPA 엔티티 (Store, Product, Order)
├── dto/                 # 요청/응답 DTO
├── repository/          # 데이터 접근 계층
├── service/             # 공통 비즈니스 로직
├── platform/            # 플랫폼 추상화 인터페이스
│
├── naver/               # 네이버 스마트스토어 통합
│   ├── client/          # API HTTP 클라이언트
│   ├── config/          # API 설정 프로퍼티
│   ├── converter/       # 엑셀 → 상품 변환기 (회사별)
│   ├── service/         # 상품/카테고리/원산지 서비스
│   └── util/            # BCrypt 서명, 카테고리 검증
│
└── coupang/             # 쿠팡 Open API 통합
    ├── client/          # API HTTP 클라이언트
    ├── config/          # API 설정 프로퍼티
    ├── converter/       # 엑셀 → 상품 변환기 (회사별)
    ├── service/         # 상품/카테고리 서비스
    └── util/            # HMAC SHA256 서명
```

### 설계 패턴

- **Strategy Pattern** — `ProductService`, `BatchUploadService` 인터페이스로 플랫폼별 구현 분리. 런타임에 `platform` 파라미터로 동적 선택
- **Converter Pattern** — 회사마다 다른 엑셀 컬럼 포맷을 별도 Converter 클래스로 처리. 신규 회사 포맷 추가 시 Converter 하나만 구현
- **Non-Transactional Batch** — 엑셀 행 단위 독립 처리. 한 행 실패가 전체를 중단시키지 않으며 행별 성공/실패 리포트 생성

---

## API 엔드포인트

### Store (스토어 관리)
```
POST   /api/stores              스토어 등록
GET    /api/stores              현재 스토어 조회
PUT    /api/stores              스토어 수정
DELETE /api/stores              스토어 삭제
```

### OAuth (인증)
```
POST   /api/oauth/authenticate        네이버 OAuth 인증 (form params)
POST   /api/oauth/authenticate/json   네이버 OAuth 인증 (JSON body)
```

### Products (상품)
```
POST   /api/products/search                      상품 검색 (platform, 페이지네이션)
GET    /api/products/categories                  카테고리 목록
POST   /api/products/upload-excel                엑셀 일괄 등록
POST   /api/products/upload-excel-group-products 그룹 상품 일괄 등록
POST   /api/products/{productId}                 단건 등록
GET    /api/products/{productId}                 상품 상세
PUT    /api/products/{productId}                 상품 수정
```

**공통 쿼리 파라미터**

| 파라미터 | 값 | 설명 |
|----------|----|----|
| `platform` | `naver` / `coupang` / `11st` | 대상 플랫폼 (기본: naver) |
| `company` | 회사명 문자열 | 엑셀 컬럼 매핑 기준 회사 (기본: TestCompany) |

### Sync (동기화)
```
POST   /api/sync/products        모든 플랫폼 상품 동기화
POST   /api/sync/orders          주문 동기화 (startDate, endDate 선택)
```

---

## 시작하기

### 사전 요구사항

- JDK 21+
- MySQL 8.0+
- Gradle (또는 동봉된 `gradlew` 사용)
- 네이버 스마트스토어 API 키 또는 쿠팡 Open API 키


### 1. 접속

| URL | 설명 |
|-----|------|
| http://localhost:8080 | 플랫폼 선택 홈 |
| http://localhost:8080/naver | 네이버 스마트스토어 UI |
| http://localhost:8080/coupang | 쿠팡 UI |
| http://localhost:8080/swagger-ui.html | API 문서 (Swagger) |

---

## 엑셀 일괄 등록 사용 방법

1. 엑셀 파일(.xlsx)을 준비합니다. 컬럼 구조는 `company` 파라미터에 맞는 Converter를 따릅니다.
2. `/api/products/upload-excel` 엔드포인트에 `multipart/form-data`로 파일을 전송합니다.
3. 처리 결과는 JSON으로 즉시 반환되며, 성공/실패 내역 CSV 파일이 `exports/` 디렉터리에 저장됩니다.

```http
POST /api/products/upload-excel?platform=naver&company=TestCompany
Content-Type: multipart/form-data

file: products.xlsx
```

**응답 예시**

```json
{
  "totalCount": 100,
  "successCount": 97,
  "failureCount": 3,
  "failures": [
    { "row": 12, "reason": "카테고리를 찾을 수 없음: '가전 > 미존재'" },
    ...
  ]
}
```

---

## 신규 플랫폼 / 회사 포맷 추가

### 새 플랫폼 추가

1. `platform/ProductService` 인터페이스 구현
2. `platform/BatchUploadService` 인터페이스 구현
3. `PlatformConstants`에 플랫폼 코드 등록
4. (선택) UI 페이지 추가

### 새 회사 엑셀 포맷 추가

1. `naver/converter/NaverExcelConverter` (또는 `coupang`) 인터페이스 구현
2. 회사명을 Bean 이름으로 사용하여 Spring 컴포넌트 등록
3. API 호출 시 `company=YourCompanyName` 파라미터로 자동 선택

---

## 주의사항

- **`spring.jpa.hibernate.ddl-auto=create`** — 애플리케이션 시작 시 테이블이 재생성됩니다. 운영 환경에서는 반드시 `update` 또는 `validate`로 변경하세요.
- **서버 시각 동기화** — 네이버·쿠팡 API 요청 서명에 타임스탬프가 포함됩니다. 서버 시각이 실제 시각과 크게 차이나면 인증 오류가 발생합니다.
- **단일 스토어 모델** — 현재 하나의 스토어(판매자 계정)만 등록·관리할 수 있습니다.

---

## 라이선스

This project is private and proprietary. All rights reserved.

Copyright © 2026 atolow. Unauthorized copying, distribution, or modification is strictly prohibited.

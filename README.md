# cmj-portal-app

> ⚠️ **참고용 예시 코드입니다.** 실제 프로젝트 코드가 아니며, 면접 대비를 위해 기억하는 구조와 대화로 확정한 사항을 바탕으로 다시 짠 것입니다.
> 의존성 다운로드가 막힌 환경에서 작성해 **컴파일·실행은 검증하지 않았습니다.**

## 역할
포털 API. 메뉴·화면 권한 조회, 관리자 권한 매핑(저장 후 커밋 시점에 대상 사용자 세션 갱신), 정책 라벨·다국어 라벨·포털 테마, 마이페이지(언어·MFA Y/N).

## 빌드
```bash
# 1) 공통 라이브러리를 먼저 로컬에 배포 (cmj-common-security 저장소에서)
./gradlew publishToMavenLocal
# 2) 이 저장소 빌드
./gradlew build
```

## 이름 변경 안내
실제 고객사·국가를 드러내는 이름은 모두 `cmj-choiminjeong` 형태의 예시 이름으로 바꿨습니다.
- 고객사 이름 → `cmj-choiminjeong` (예: `cmj-choiminjeong 직원`, `ad.cmj-choiminjeong.example`, `hr-db.cmj-choiminjeong.internal`)
- 국가 시간대 → 설정값 `cmj.batch.zone` (예시 값 `Asia/Seoul`)
- 국가·정치 관련 설명 → "고객사 요청" 같은 일반 표현

## 관련 저장소
| 저장소 | 역할 |
|---|---|
| cmj-common-security | 공통 보안 라이브러리 (서버) — Redis 세션·요청마다 세션·권한 검사 |
| cmj-gateway | 게이트웨이 — JWT 서명·만료 확인 |
| cmj-auth-server | 토큰 발급 (OAuth2) |
| cmj-security-server | 로그인 규칙 |
| cmj-portal-app | 포털·관리자 API |
| cmj-spring-batch | 인사 동기화 배치 |
| cmj-redis | Redis에 저장할 값(라벨·공통 코드 등) 정의 + 기동 시 DB→Redis 적재 |
| cmj-frontend-common | 공통 화면 코드 |
| cmj-frontend-service | 업무 화면 코드 |

⚠️ 공통 라이브러리의 실제 배포 방식(사내 패키지 저장소 등)은 확인되지 않아, 예시는 mavenLocal 을 씁니다.

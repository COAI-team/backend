# Code-Nose Backend

## 개요
Code-Nose 프로젝트의 백엔드는 Spring Boot 기반으로 구축된 풀스택 웹 애플리케이션의 서버 측 구현입니다. GitHub OAuth 인증, 실시간 WebSocket 통신, AI LLM 통합, 결제 시스템 등을 주요 기능으로 제공합니다.

## 기술 스택
Backend Framework: Spring Boot, Spring Security, Spring WebFlux  
ORM: MyBatis  
Database: MySQL, PostgreSQL, Redis  
Real-time: WebSocket, SSE (Server-Sent Events)  
Monitoring: Elastic Kibana, Langfuse  
Deployment: Docker, Jenkins CI/CD, AWS EC2, Nginx  
Storage: Amazon S3  
Vector DB: Qdrant  
Payments: Toss Payments  


## 아키텍처 구성

### 인증 시스템
Frontend → GitHubLoginController → GitHubOAuthService → UserService → JwtProvider → Redis/DB

text
- **GitHub OAuth Flow**: `auth/github/callback?code&state` 처리
- **JWT 토큰**: Access/Refresh 토큰 생성 (Redis TTL 14일)
- **필터**: `JwtAuthenticationFilter`로 요청 검증

## 주요 컴포넌트

### Controllers
├── GitHubLoginController (OAuth callback)  
├── HealthController (헬스 체크)  
├── AdminUserBoardController (관리자 API)  
└── ChatController (메시지 처리)

### Services
├── GitHubOAuthService  
├── UserService  
└── PaymentService (Toss 연동)

### Aspects
└── PerformanceMonitoringAspect (AOP 성능 모니터링)

## 핵심 기능

### 1. GitHub OAuth 인증 플로우
GET /auth/github → GitHub authorize 리다이렉트

GET /auth/github/callback?code=xxx&state=yyy

GitHubUser 정보 조회 → Users 테이블 확인 (needLink 여부)

JWT 토큰 생성 → Redis refresh 토큰 저장

200 OK + GitHubCallbackResponse 반환


### 2. 실시간 통신 (WebSocket)
WS Graceful Shutdown 처리

Redis Lock으로 동시성 제어

COUNTDOWN, 1vs1 Battle 모드 지원

LocalDateTime 직렬화 (JavaTimeModule)

### 3. 결제 시스템 (Toss Payments)
Internal DB: Order 생성 (orderId, amount)

External: Toss READY 요청

Toss Confirm API 호출

상태: READY → PROCESSING → DONE/CANCELED

## 배포 환경
  
Infrastructure  
├── AWS EC2  
│   └── Docker 컨테이너  
├── Jenkins  
│   └── GitHub Push → Docker Build → Deploy  
├── Nginx  
│   └── Reverse Proxy  
├── RDS  
│   └── MySQL / PostgreSQL  
├── Redis  
│   └── Session / Cache / Lock  
├── Elastic Stack  
│   └── Logging / Monitoring  
└── S3  
└── Static Files  

## 모니터링 및 로깅  
PerformanceMonitoringAspect  
├── START / SUCCESS / ERROR 로그  
├── SLOW / VERYSLOW 로그  
└── 실행 시간 측정  
└── ms 단위  

Kibana Logs  
├── WebSocketMessageBrokerStats  
├── JwtAuthenticationFilter  
└── Controller별 성능 메트릭


## 보안 대책
SQL Injection: PreparedStatement 사용

XSS: Frontend React + Backend HTML 이스케이프

JWT: Redis refresh 토큰 TTL 관리

CORS: Spring Boot 설정
-- =============================================
-- AUTO_INCREMENT 적용된 최종 알고리즘 도메인 MySQL DDL
-- =============================================
-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS `algorithm_platform` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `algorithm_platform`;
-- =============================================
-- 0. 사용자 테이블 (외래키 참조를 위해 먼저 생성)
-- =============================================
-- CREATE TABLE `USERS` (
--     `USER_ID` BIGINT AUTO_INCREMENT PRIMARY KEY,
--     `USER_EMAIL` VARCHAR(255) NOT NULL,
--     `USER_PW` VARCHAR(255) NOT NULL,
--     `USER_NAME` VARCHAR(100) NOT NULL,
--     `USER_NICKNAME` VARCHAR(50) NOT NULL,
--     `USER_IMAGE` VARCHAR(500) NULL,
--     `USER_GRADE` INT DEFAULT 1 NOT NULL,
--     `USER_ROLE` ENUM('ROLE_USER', 'ROLE_ADMIN') DEFAULT 'ROLE_USER' NOT NULL,
--     `USER_ISDELETED` TINYINT(1) DEFAULT 0 NOT NULL,
--     `USER_DELETEDAT` DATETIME NULL,
--     `USER_CREATEDAT` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
--     `USER_UPDATEDAT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
--     `USER_ENABLED` TINYINT(1) DEFAULT 1 NOT NULL,
--     `USER_ISSUBSCRIBED` TINYINT(1) DEFAULT 0 NOT NULL,
--     CONSTRAINT `UQ_USERS_EMAIL` UNIQUE (`USER_EMAIL`),
--     CONSTRAINT `UQ_USERS_NICKNAME` UNIQUE (`USER_NICKNAME`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자';
-- =============================================
-- 1. 알고리즘 문제 테이블 (SQL 문제 지원 추가)
-- =============================================
CREATE TABLE `ALGO_PROBLEMS` (
    `ALGO_PROBLEM_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '문제 고유 식별자',
    `ALGO_PROBLEM_TITLE` VARCHAR(255) NOT NULL COMMENT '문제 제목',
    `ALGO_PROBLEM_DESCRIPTION` TEXT NOT NULL COMMENT '문제 상세 설명',
    `ALGO_PROBLEM_DIFFICULTY` ENUM('BRONZE', 'SILVER', 'GOLD', 'PLATINUM') NOT NULL COMMENT '문제 난이도',
    `ALGO_PROBLEM_SOURCE` ENUM('AI_GENERATED', 'BOJ', 'CUSTOM') NOT NULL COMMENT '문제 생성 출처',
    -- 문제 유형 및 SQL 문제용 스크립트 추가
    `PROBLEM_TYPE` ENUM('ALGORITHM', 'SQL') DEFAULT 'ALGORITHM' NOT NULL COMMENT '문제 유형',
    `INIT_SCRIPT` TEXT NULL COMMENT 'SQL 문제용 초기화 스크립트 (CREATE/INSERT)',
    -- LANGUAGE 컬럼 제거됨 (LANGUAGE_CONSTANTS 테이블로 대체)
    `TIMELIMIT` INT DEFAULT 1000 COMMENT '기본 시간 제한(ms)',
    `MEMORYLIMIT` INT DEFAULT 256 COMMENT '기본 메모리 제한(MB)',
    `ALGO_CREATER` BIGINT NULL COMMENT '문제 생성자 ID',
    `ALGO_CREATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `ALGO_UPDATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `ALGO_PROBLEM_TAGS` JSON NULL COMMENT '문제 태그 배열',
    `ALGO_PROBLEM_STATUS` TINYINT(1) DEFAULT 1 COMMENT '문제 활성화 상태',
    -- 인덱스
    INDEX `idx_difficulty_source` (`ALGO_PROBLEM_DIFFICULTY`, `ALGO_PROBLEM_SOURCE`),
    INDEX `idx_created_at` (`ALGO_CREATED_AT` DESC),
    INDEX `idx_problem_status` (`ALGO_PROBLEM_STATUS`),
    INDEX `idx_problem_type` (`PROBLEM_TYPE`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '알고리즘 문제';
-- =============================================
-- 2. 언어별 상수 테이블 (언어 유형 추가)
-- =============================================
CREATE TABLE `LANGUAGE_CONSTANTS` (
    `LANGUAGE_NAME` VARCHAR(50) PRIMARY KEY COMMENT '언어명',
    `LANGUAGE_TYPE` ENUM('GENERAL', 'DB') DEFAULT 'GENERAL' NOT NULL COMMENT '언어 유형',
    `TIME_FACTOR` DECIMAL(3, 1) DEFAULT 1.0 COMMENT '시간 제한 배수',
    `TIME_ADDITION` INT DEFAULT 0 COMMENT '시간 제한 추가(ms)',
    `MEMORY_FACTOR` DECIMAL(3, 1) DEFAULT 1.0 COMMENT '메모리 제한 배수',
    `MEMORY_ADDITION` INT DEFAULT 0 COMMENT '메모리 제한 추가(MB)',
    INDEX `idx_language_type` (`LANGUAGE_TYPE`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '언어별 채점 상수';
-- =============================================
-- 3. 알고리즘 테스트케이스 테이블
-- =============================================
CREATE TABLE `ALGO_TESTCASES` (
    `TESTCASE_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '테스트케이스 고유 식별자',
    `INPUT_DATA` TEXT NOT NULL COMMENT '테스트 입력값',
    `EXPECTED_OUTPUT` TEXT NOT NULL COMMENT '예상 출력값',
    `IS_SAMPLE` TINYINT(1) DEFAULT 0 COMMENT '샘플 테스트케이스 여부',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 고유 식별자',
    -- 외래키
    FOREIGN KEY (`ALGO_PROBLEM_ID`) REFERENCES `ALGO_PROBLEMS`(`ALGO_PROBLEM_ID`) ON DELETE CASCADE,
    -- 인덱스
    INDEX `idx_problem_id` (`ALGO_PROBLEM_ID`),
    INDEX `idx_sample` (`IS_SAMPLE`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '알고리즘 테스트케이스';
-- =============================================
-- 4. 알고리즘 제출 테이블
-- =============================================
-- 변경사항:
-- - FOCUS_SCORE, FOCUS_SESSION_ID, EYETRACKED 컬럼 제거 (모니터링이 점수에 미반영)
-- - SOLVE_MODE 추가: 'BASIC'(자유 풀이) vs 'FOCUS'(집중 모드 - 시간제한+모니터링)
-- - MONITORING_SESSION_ID 추가: 모니터링 세션과 연결
CREATE TABLE `ALGO_SUBMISSIONS` (
    `ALGOSUBMISSION_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '제출 고유 식별자',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 고유 식별자',
    `USER_ID` BIGINT NOT NULL COMMENT '제출자 ID',
    `SOURCE_CODE` TEXT NOT NULL COMMENT '제출한 소스코드',
    `LANGUAGE` VARCHAR(50) NOT NULL COMMENT '제출 언어',
    `EXECUTION_TIME` INT NULL COMMENT '실행 시간(ms)',
    `MEMORY_USAGE` INT NULL COMMENT '메모리 사용량(MB)',
    `PASSED_TEST_COUNT` INT DEFAULT 0 COMMENT '통과한 테스트케이스 수',
    `TOTAL_TEST_COUNT` INT DEFAULT 0 COMMENT '전체 테스트케이스 수',
    `AI_FEEDBACK` TEXT NULL COMMENT 'AI 코드 리뷰 결과',
    `AI_FEEDBACK_STATUS` ENUM('PENDING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT 'AI 리뷰 진행 상태',
    `AI_FEEDBACK_TYPE` ENUM(
        'QUALITY',
        'PERFORMANCE',
        'STYLE',
        'COMPREHENSIVE'
    ) DEFAULT 'COMPREHENSIVE' COMMENT 'AI 피드백 유형',
    `JUDGE_RESULT` ENUM('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'PENDING') DEFAULT 'PENDING' COMMENT '채점 결과',
    `SOLVE_MODE` ENUM('BASIC', 'FOCUS') DEFAULT 'BASIC' COMMENT '풀이 모드 (BASIC: 자유, FOCUS: 집중모드)',
    `MONITORING_SESSION_ID` VARCHAR(255) NULL COMMENT '연결된 모니터링 세션 ID (FOCUS 모드일 때만)',
    `AI_SCORE` DECIMAL(5, 2) DEFAULT 0.00 COMMENT 'AI 코드 품질 점수 (0-100)',
    `TIME_EFFICIENCY_SCORE` DECIMAL(5, 2) DEFAULT 0.00 COMMENT '시간 효율성 점수 (0-100)',
    `FINAL_SCORE` DECIMAL(5, 2) DEFAULT 0.00 COMMENT '최종 종합 점수 (0-100)',
    `SCORE_WEIGHTS` JSON NULL COMMENT '점수 가중치 정보',
    `STARTSOLVING` TIMESTAMP NULL COMMENT '문제 풀이 시작 시각',
    `ENDSOLVING` TIMESTAMP NULL COMMENT '문제 풀이 종료 시각',
    `SOLVING_DURATION_SECONDS` INT NULL COMMENT '총 풀이 소요 시간(초)',
    `GITHUB_COMMIT_REQUESTED` TINYINT(1) DEFAULT 0 COMMENT 'GitHub 자동 커밋 요청 여부',
    `GITHUB_COMMIT_STATUS` ENUM('NONE', 'PENDING', 'COMPLETED', 'FAILED') DEFAULT 'NONE' COMMENT 'GitHub 커밋 진행 상태',
    `SUBMITTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '제출 시각',
    `IS_SHARED` TINYINT(1) DEFAULT 0 COMMENT '게시글로 공유 여부',
    -- 외래키
    FOREIGN KEY (`ALGO_PROBLEM_ID`) REFERENCES `ALGO_PROBLEMS`(`ALGO_PROBLEM_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    -- 인덱스
    INDEX `idx_user_problem` (`USER_ID`, `ALGO_PROBLEM_ID`),
    INDEX `idx_final_score` (`FINAL_SCORE` DESC),
    INDEX `idx_submitted_at` (`SUBMITTED_AT` DESC),
    INDEX `idx_monitoring_session` (`MONITORING_SESSION_ID`),
    INDEX `idx_judge_result` (`JUDGE_RESULT`),
    INDEX `idx_solve_mode` (`SOLVE_MODE`)
) ENGINE = InnoDB AUTO_INCREMENT = 1000 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '알고리즘 제출 기록';
-- =============================================
-- 5. 모니터링 세션 테이블 (집중 모드 전용)
-- =============================================
-- 설계 근거:
-- 1. 기존 FOCUS_SESSIONS + FOCUS_SUMMARY + VIOLATION_LOGS 3개 테이블을 1개로 통합
-- 2. 위반 유형별 개별 카운트 컬럼 사용 (JSON 대신) → 쿼리 단순화, 인덱스 활용 가능
-- 3. 모니터링은 점수에 영향 없음 (정보 제공 및 경고 목적)
-- 4. FOCUS 모드에서만 생성됨 (BASIC 모드에서는 생성 안함)
CREATE TABLE `MONITORING_SESSIONS` (
    `SESSION_ID` VARCHAR(255) PRIMARY KEY COMMENT '세션 고유 식별자 (UUID)',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 ID',
    `ALGOSUBMISSION_ID` BIGINT NULL COMMENT '연결된 제출 ID (제출 후 연결)',
    -- 세션 상태 및 시간
    `SESSION_STATUS` ENUM('ACTIVE', 'COMPLETED', 'TIMEOUT', 'TERMINATED') DEFAULT 'ACTIVE' COMMENT '세션 상태',
    `STARTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 시작 시각',
    `ENDED_AT` TIMESTAMP NULL COMMENT '세션 종료 시각',
    -- 시간 제한 설정
    `TIME_LIMIT_MINUTES` INT NOT NULL COMMENT '설정된 제한 시간(분)',
    `REMAINING_SECONDS` INT NULL COMMENT '종료 시 남은 시간(초)',
    `AUTO_SUBMITTED` TINYINT(1) DEFAULT 0 COMMENT '시간 초과로 자동 제출 여부',
    -- 위반 유형별 카운트 (각 모니터링 요소)
    `GAZE_AWAY_COUNT` INT DEFAULT 0 COMMENT '시선 이탈 횟수',
    `SLEEPING_COUNT` INT DEFAULT 0 COMMENT '졸음 감지 횟수',
    `NO_FACE_COUNT` INT DEFAULT 0 COMMENT '얼굴 미감지 횟수 (자리비움)',
    `MASK_DETECTED_COUNT` INT DEFAULT 0 COMMENT '마스크 착용 감지 횟수',
    `MULTIPLE_FACES_COUNT` INT DEFAULT 0 COMMENT '복수 인원 감지 횟수',
    `MOUSE_LEAVE_COUNT` INT DEFAULT 0 COMMENT '마우스 화면 이탈 횟수',
    `TAB_SWITCH_COUNT` INT DEFAULT 0 COMMENT '탭/브라우저 전환 횟수',
    `FULLSCREEN_EXIT_COUNT` INT DEFAULT 0 COMMENT '전체화면 해제 횟수',
    -- 집계
    `TOTAL_VIOLATIONS` INT DEFAULT 0 COMMENT '총 위반 횟수',
    `WARNING_SHOWN_COUNT` INT DEFAULT 0 COMMENT '경고 팝업 표시 횟수',
    -- 외래키
    FOREIGN KEY (`ALGO_PROBLEM_ID`) REFERENCES `ALGO_PROBLEMS`(`ALGO_PROBLEM_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    -- 인덱스
    INDEX `idx_user_started` (`USER_ID`, `STARTED_AT` DESC),
    INDEX `idx_session_status` (`SESSION_STATUS`),
    INDEX `idx_problem_user` (`ALGO_PROBLEM_ID`, `USER_ID`),
    INDEX `idx_submission` (`ALGOSUBMISSION_ID`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '집중 모드 모니터링 세션';
-- =============================================
-- 6. GitHub 커밋 테이블
-- =============================================
CREATE TABLE `GITHUB_COMMITS` (
    `COMMIT_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '커밋 고유 식별자',
    `ALGOSUBMISSION_ID` BIGINT NOT NULL COMMENT '연결된 제출 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `GITHUB_USERNAME` VARCHAR(100) NOT NULL COMMENT 'GitHub 사용자명',
    `REPOSITORY_NAME` VARCHAR(100) NOT NULL COMMENT '리포지토리명',
    `BRANCH_NAME` VARCHAR(100) DEFAULT 'main' COMMENT '브랜치명',
    `INCLUDE_AI_FEEDBACK` TINYINT(1) DEFAULT 1 COMMENT 'AI 피드백 포함 여부',
    `CUSTOM_COMMIT_MESSAGE` TEXT NULL COMMENT '사용자 지정 커밋 메시지',
    `COMMIT_STATUS` ENUM('PENDING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT '커밋 진행 상태',
    `COMMIT_URL` VARCHAR(500) NULL COMMENT 'GitHub 커밋 URL',
    `REQUESTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '커밋 요청 시각',
    `COMPLETED_AT` TIMESTAMP NULL COMMENT '커밋 완료 시각',
    `ERROR_MESSAGE` TEXT NULL COMMENT '커밋 실패 시 에러 메시지',
    FOREIGN KEY (`ALGOSUBMISSION_ID`) REFERENCES `ALGO_SUBMISSIONS`(`ALGOSUBMISSION_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    INDEX `idx_submission` (`ALGOSUBMISSION_ID`),
    INDEX `idx_user_status` (`USER_ID`, `COMMIT_STATUS`),
    INDEX `idx_requested_at` (`REQUESTED_AT` DESC)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'GitHub 자동 커밋';
-- =============================================
-- (삭제됨) 부정행위 로그 테이블
-- =============================================
-- 변경 사유: VIOLATION_LOGS 테이블 삭제
-- 개별 위반 로그 대신 MONITORING_SESSIONS 테이블의 각 위반 유형별 카운트 컬럼으로 대체
-- 이유:
-- 1. 점수에 반영하지 않으므로 상세 로그 불필요
-- 2. 경고 목적으로는 실시간 카운트만 필요
-- 3. 테이블 구조 단순화 및 성능 향상
-- =============================================
-- AUTO_INCREMENT 시작값 설정
-- =============================================
ALTER TABLE `ALGO_PROBLEMS` AUTO_INCREMENT = 1;
ALTER TABLE `ALGO_TESTCASES` AUTO_INCREMENT = 1;
ALTER TABLE `ALGO_SUBMISSIONS` AUTO_INCREMENT = 1000;
ALTER TABLE `GITHUB_COMMITS` AUTO_INCREMENT = 1;
-- =============================================
-- 언어별 상수 데이터 삽입 (제공된 표 반영 + LANGUAGE_TYPE)
-- =============================================
INSERT INTO `LANGUAGE_CONSTANTS` (
        `LANGUAGE_NAME`,
        `LANGUAGE_TYPE`,
        `TIME_FACTOR`,
        `TIME_ADDITION`,
        `MEMORY_FACTOR`,
        `MEMORY_ADDITION`
    )
VALUES -- C/C++ 계열 (기본값)
    ('C++17', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C11', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C99', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++98', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++11', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++14', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++20', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++23', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++26', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C (Clang)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C++ (Clang)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C17', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C23', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C90', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C2x', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C90 (Clang)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C2x (Clang)', 'GENERAL', 1.0, 0, 1.0, 0),
    -- Java 계열 (x2 + 1초, x2 + 16MB)
    ('Java 8', 'GENERAL', 2.0, 1000, 2.0, 16),
    (
        'Java 8 (OpenJDK)',
        'GENERAL',
        2.0,
        1000,
        2.0,
        16
    ),
    ('Java 11', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java 15', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java (JDK 17)', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java (JDK 21)', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java 17', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java 21', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Java 19', 'GENERAL', 2.0, 1000, 2.0, 16),
    -- Python 계열 (x3 + 2초, x2 + 32MB/128MB)
    ('Python 3', 'GENERAL', 3.0, 2000, 2.0, 32),
    ('PyPy3', 'GENERAL', 3.0, 2000, 2.0, 128),
    ('Python 2', 'GENERAL', 3.0, 2000, 2.0, 32),
    ('PyPy2', 'GENERAL', 3.0, 2000, 2.0, 128),
    -- 기타 언어
    ('Ruby', 'GENERAL', 2.0, 1000, 1.0, 512),
    ('Kotlin (JVM)', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Kotlin (Native)', 'GENERAL', 1.0, 0, 1.0, 16),
    ('Swift', 'GENERAL', 1.0, 0, 1.0, 512),
    ('Swift (Apple)', 'GENERAL', 1.0, 0, 1.0, 512),
    ('Text', 'GENERAL', 1.0, 0, 1.0, 0),
    ('C#', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('node.js', 'GENERAL', 3.0, 2000, 2.0, 2),
    ('Go', 'GENERAL', 1.0, 2000, 1.0, 512),
    ('Go (gccgo)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('D', 'GENERAL', 1.0, 0, 1.0, 16),
    ('D (LDC)', 'GENERAL', 1.0, 0, 1.0, 16),
    ('F# (Mono)', 'GENERAL', 3.0, 2000, 2.0, 32),
    ('Pascal', 'GENERAL', 1.0, 0, 1.0, 64),
    ('Haskell', 'GENERAL', 2.0, 1000, 1.0, 16),
    ('Rust 2018', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Rust 2021', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Rust', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Lua', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('Perl', 'GENERAL', 3.0, 2000, 2.0, 16),
    ('PHP', 'GENERAL', 3.0, 2000, 2.0, 16),
    ('Clojure', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Fortran', 'GENERAL', 1.0, 0, 1.0, 0),
    (
        'Scheme (Chicken)',
        'GENERAL',
        3.0,
        2000,
        2.0,
        32
    ),
    ('Scheme (Racket)', 'GENERAL', 3.0, 2000, 2.0, 16),
    ('Assembly (32bit)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Assembly (64bit)', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Objective-C', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Objective-C++', 'GENERAL', 1.0, 0, 1.0, 0),
    ('아희 (Aheui)', 'GENERAL', 1.0, 2000, 1.0, 64),
    (
        '아희 (Aheui) (Bok-sil)',
        'GENERAL',
        1.0,
        2000,
        1.0,
        64
    ),
    ('아희 (Aheui) (C)', 'GENERAL', 1.0, 1000, 1.0, 0),
    ('Golfscript', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('Brainf**k', 'GENERAL', 1.0, 1000, 1.0, 0),
    ('Whitespace', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Tcl', 'GENERAL', 1.0, 2000, 1.0, 512),
    ('Rhino', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Cobol', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Pike', 'GENERAL', 3.0, 2000, 2.0, 16),
    ('Sed', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('Bash', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('Ada', 'GENERAL', 1.0, 0, 1.0, 0),
    ('AWK', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('OCaml', 'GENERAL', 1.0, 0, 1.0, 64),
    ('Perl 6', 'GENERAL', 3.0, 2000, 2.0, 16),
    ('Vim', 'GENERAL', 1.0, 2000, 1.0, 64),
    ('Haxe', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Nim', 'GENERAL', 1.0, 0, 1.0, 16),
    ('Algol 68', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Befunge', 'GENERAL', 1.0, 0, 1.0, 32),
    ('Ceylon', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Pony', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Nemerle', 'GENERAL', 1.0, 5000, 1.0, 512),
    ('Cobra', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('Nimrod', 'GENERAL', 1.0, 0, 1.0, 0),
    ('Pascal (FPC)', 'GENERAL', 1.0, 0, 1.0, 64),
    ('TypeScript', 'GENERAL', 3.0, 2000, 2.0, 2),
    ('Visual Basic', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('MonoDevelop C#', 'GENERAL', 2.0, 1000, 2.0, 16),
    ('F# (.NET)', 'GENERAL', 2.0, 1000, 2.0, 16),
    -- DB 언어 (SQL 문제용)
    ('MySQL', 'DB', 1.0, 0, 1.0, 0),
    ('PostgreSQL', 'DB', 1.0, 0, 1.0, 0),
    ('SQLite', 'DB', 1.0, 0, 1.0, 0);
-- =============================================
-- 샘플 데이터 삽입 (문제)
-- =============================================
INSERT INTO `ALGO_PROBLEMS` (
        `ALGO_PROBLEM_TITLE`,
        `ALGO_PROBLEM_DESCRIPTION`,
        `ALGO_PROBLEM_DIFFICULTY`,
        `ALGO_PROBLEM_SOURCE`,
        `PROBLEM_TYPE`,
        `TIMELIMIT`,
        `MEMORYLIMIT`,
        `ALGO_PROBLEM_TAGS`
    )
VALUES (
        '두 수의 합',
        '두 정수를 입력받아 합을 출력하는 프로그램을 작성하시오.',
        'BRONZE',
        'BOJ',
        'ALGORITHM',
        1000,
        128,
        '["수학", "구현"]'
    ),
    (
        '피보나치 수',
        'n번째 피보나치 수를 구하는 프로그램을 작성하시오.',
        'SILVER',
        'AI_GENERATED',
        'ALGORITHM',
        2000,
        256,
        '["동적프로그래밍", "수학"]'
    ),
    (
        '최단경로',
        '그래프에서 최단경로를 구하는 프로그램을 작성하시오.',
        'GOLD',
        'CUSTOM',
        'ALGORITHM',
        3000,
        512,
        '["그래프", "다익스트라"]'
    );
-- 샘플 테스트케이스
INSERT INTO `ALGO_TESTCASES` (
        `ALGO_PROBLEM_ID`,
        `INPUT_DATA`,
        `EXPECTED_OUTPUT`,
        `IS_SAMPLE`
    )
VALUES (1, '1 2', '3', 1),
    (1, '5 7', '12', 1),
    (1, '0 0', '0', 0),
    (2, '1', '1', 1),
    (2, '5', '5', 1),
    (2, '10', '55', 0);
-- =============================================
-- 유용한 뷰 생성
-- =============================================
-- 사용자별 종합 통계 뷰
-- 변경: FOCUS_SCORE 제거 (모니터링이 점수에 미반영)
-- 추가: solve_mode별 통계
CREATE VIEW `V_USER_STATS` AS s.USER_ID,
COUNT(s.ALGOSUBMISSION_ID) AS total_submissions,
COUNT(
    CASE
        WHEN s.JUDGE_RESULT = 'AC' THEN 1
    END
) AS accepted_count,
ROUND(AVG(s.FINAL_SCORE), 2) AS avg_final_score,
COUNT(
    CASE
        WHEN s.SOLVE_MODE = 'FOCUS' THEN 1
    END
) AS focus_mode_count,
COUNT(
    CASE
        WHEN s.SOLVE_MODE = 'BASIC' THEN 1
    END
) AS basic_mode_count,
COUNT(DISTINCT s.ALGO_PROBLEM_ID) AS unique_problems
FROM ALGO_SUBMISSIONS s
GROUP BY s.USER_ID;
-- 문제별 통계 뷰
CREATE VIEW `V_PROBLEM_STATS` AS
SELECT p.ALGO_PROBLEM_ID,
    p.ALGO_PROBLEM_TITLE,
    p.ALGO_PROBLEM_DIFFICULTY,
    COUNT(s.ALGOSUBMISSION_ID) AS total_attempts,
    COUNT(
        CASE
            WHEN s.JUDGE_RESULT = 'AC' THEN 1
        END
    ) AS success_count,
    ROUND(AVG(s.FINAL_SCORE), 2) AS avg_score
FROM ALGO_PROBLEMS p
    LEFT JOIN ALGO_SUBMISSIONS s ON p.ALGO_PROBLEM_ID = s.ALGO_PROBLEM_ID
GROUP BY p.ALGO_PROBLEM_ID,
    p.ALGO_PROBLEM_TITLE,
    p.ALGO_PROBLEM_DIFFICULTY;
-- 최신 제출 조회 뷰
CREATE VIEW `V_RECENT_SUBMISSIONS` AS
SELECT s.ALGOSUBMISSION_ID,
    s.USER_ID,
    p.ALGO_PROBLEM_TITLE,
    s.JUDGE_RESULT,
    s.FINAL_SCORE,
    s.SUBMITTED_AT
FROM ALGO_SUBMISSIONS s
    JOIN ALGO_PROBLEMS p ON s.ALGO_PROBLEM_ID = p.ALGO_PROBLEM_ID
ORDER BY s.ALGOSUBMISSION_ID DESC
LIMIT 100;
-- 문제 난이도별 통계 뷰
CREATE VIEW `V_DIFFICULTY_STATS` AS
SELECT p.ALGO_PROBLEM_DIFFICULTY,
    COUNT(DISTINCT p.ALGO_PROBLEM_ID) AS problem_count,
    COUNT(s.ALGOSUBMISSION_ID) AS total_submissions,
    COUNT(
        CASE
            WHEN s.JUDGE_RESULT = 'AC' THEN 1
        END
    ) AS success_submissions,
    ROUND(
        COUNT(
            CASE
                WHEN s.JUDGE_RESULT = 'AC' THEN 1
            END
        ) * 100.0 / NULLIF(COUNT(s.ALGOSUBMISSION_ID), 0),
        2
    ) AS success_rate,
    ROUND(AVG(s.FINAL_SCORE), 2) AS avg_score
FROM ALGO_PROBLEMS p
    LEFT JOIN ALGO_SUBMISSIONS s ON p.ALGO_PROBLEM_ID = s.ALGO_PROBLEM_ID
GROUP BY p.ALGO_PROBLEM_DIFFICULTY
ORDER BY CASE
        p.ALGO_PROBLEM_DIFFICULTY
        WHEN 'BRONZE' THEN 1
        WHEN 'SILVER' THEN 2
        WHEN 'GOLD' THEN 3
        WHEN 'PLATINUM' THEN 4
    END;
-- 방성일
CREATE TABLE `USER_CODE_PATTERNS` (
    `PATTERN_ID` VARCHAR(255) NOT NULL COMMENT 'UUID',
    `USER_ID` BIGINT NOT NULL,
    `PATTERN_TYPE` VARCHAR(100) NULL COMMENT 'Long Method, Magic Number 등',
    `FREQUENCY` INT DEFAULT 0 NULL,
    `LAST_DETECTED` DATETIME NULL,
    `IMPROVEMENT_STATUS` VARCHAR(100) NULL COMMENT 'Detected, In Progress, Resolved',
    `CREATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    `UPDATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NULL,
    PRIMARY KEY (`PATTERN_ID`),
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    INDEX `idx_user_pattern` (`USER_ID`, `PATTERN_TYPE`)
) COMMENT = '사용자 코딩 습관 및 패턴';
-- 분석용 원본 파일 저장 (GitHub 파일 등)
CREATE TABLE `GITHUB_FILES` (
    `FILE_ID` VARCHAR(255) NOT NULL,
    `USER_ID` BIGINT NOT NULL,
    `REPOSITORY_URL` VARCHAR(1000) NULL,
    `OWNER` VARCHAR(255) NULL,
    `REPO` VARCHAR(255) NULL,
    `FILE_PATH` VARCHAR(1000) NULL,
    `FILE_NAME` VARCHAR(500) NULL,
    `FILE_CONTENT` LONGTEXT NULL,
    `FILE_SIZE` INT NULL,
    `ENCODING` VARCHAR(50) NULL,
    `CREATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    `UPDATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NULL,
    PRIMARY KEY (`FILE_ID`),
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE
);
-- 코드 분석 이력 (상세 리포트)
CREATE TABLE `CODE_ANALYSIS_HISTORY` (
    `ANALYSIS_ID` VARCHAR(255) NOT NULL COMMENT 'UUID',
    `USER_ID` BIGINT NOT NULL,
    `REPOSITORY_URL` VARCHAR(1000) NULL,
    `FILE_PATH` VARCHAR(1000) NULL,
    `ANALYSIS_TYPE` VARCHAR(100) NULL,
    `TONE_LEVEL` INT NULL,
    `CUSTOM_REQUIREMENTS` TEXT NULL,
    `ANALYSIS_RESULT` LONGTEXT NULL COMMENT 'JSON 문자열',
    `AI_SCORE` INT NULL,
    `CODE_SMELLS` LONGTEXT NULL COMMENT 'JSON 문자열',
    `SUGGESTIONS` LONGTEXT NULL COMMENT 'JSON 문자열',
    `CREATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    PRIMARY KEY (`ANALYSIS_ID`),
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE
);
-- 분석 결과와 패턴 매핑
CREATE TABLE `ANALYSIS_PATTERN_MAPPING` (
    `MAPPING_ID` VARCHAR(255) NOT NULL,
    `ANALYSIS_ID` VARCHAR(255) NOT NULL,
    `PATTERN_ID` VARCHAR(255) NOT NULL,
    `SEVERITY` VARCHAR(50) NULL,
    `LINE_NUMBER` INT NULL,
    `CODE_SNIPPET` TEXT NULL,
    `CREATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    PRIMARY KEY (`MAPPING_ID`),
    FOREIGN KEY (`ANALYSIS_ID`) REFERENCES `CODE_ANALYSIS_HISTORY`(`ANALYSIS_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`PATTERN_ID`) REFERENCES `USER_CODE_PATTERNS`(`PATTERN_ID`) ON DELETE CASCADE
);
-- 게시판 공유용 결과 테이블 (CODERESULT)
CREATE TABLE `CODERESULT` (
    `CODE_RESULT_ID` BIGINT NOT NULL AUTO_INCREMENT,
    `USER_ID` BIGINT NOT NULL,
    `CODE_RESULT_TITLE` VARCHAR(500) NULL,
    `CODE_FILE_URL` VARCHAR(1000) NULL,
    `ANALYSIS_DETAIL` LONGTEXT NULL,
    `SCORE` DECIMAL(5, 2) NULL,
    `REPOSITORY_URL` VARCHAR(1000) NULL,
    `FILE_PATH` VARCHAR(1000) NULL,
    `ANALYSIS_TYPE` VARCHAR(100) NULL,
    `TONE_LEVEL` INT NULL,
    `AI_SCORE` INT NULL,
    `CODE_SMELLS` LONGTEXT NULL,
    `SUGGESTIONS` LONGTEXT NULL,
    `CREATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    `UPDATED_AT` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NULL,
    PRIMARY KEY (`CODE_RESULT_ID`),
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE
);
-- =============================================
-- 성능 최적화 설정
-- =============================================
SET GLOBAL innodb_buffer_pool_size = 1073741824;
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;
SET @@auto_increment_increment = 1;
SET @@auto_increment_offset = 1;
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
SHOW TABLES;
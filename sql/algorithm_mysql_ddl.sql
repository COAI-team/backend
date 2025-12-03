-- =============================================
-- AUTO_INCREMENT 적용된 최종 알고리즘 도메인 MySQL DDL
-- =============================================

-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS `algorithm_platform` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `algorithm_platform`;

-- =============================================
-- 0. 사용자 테이블 (외래키 참조를 위해 먼저 생성)
-- =============================================
CREATE TABLE `USERS` (
    `USER_ID` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `USER_EMAIL` VARCHAR(255) NOT NULL,
    `USER_PW` VARCHAR(255) NOT NULL,
    `USER_NAME` VARCHAR(100) NOT NULL,
    `USER_NICKNAME` VARCHAR(50) NOT NULL,
    `USER_IMAGE` VARCHAR(500) NULL,
    `USER_GRADE` INT DEFAULT 1 NOT NULL,
    `USER_ROLE` ENUM('ROLE_USER', 'ROLE_ADMIN') DEFAULT 'ROLE_USER' NOT NULL,
    `USER_ISDELETED` TINYINT(1) DEFAULT 0 NOT NULL,
    `USER_DELETEDAT` DATETIME NULL,
    `USER_CREATEDAT` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    `USER_UPDATEDAT` DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    `USER_ENABLED` TINYINT(1) DEFAULT 1 NOT NULL,
    `USER_ISSUBSCRIBED` TINYINT(1) DEFAULT 0 NOT NULL,
    
    CONSTRAINT `UQ_USERS_EMAIL` UNIQUE (`USER_EMAIL`),
    CONSTRAINT `UQ_USERS_NICKNAME` UNIQUE (`USER_NICKNAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자';

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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알고리즘 문제';

-- =============================================
-- 2. 언어별 상수 테이블 (언어 유형 추가)
-- =============================================
CREATE TABLE `LANGUAGE_CONSTANTS` (
    `LANGUAGE_NAME` VARCHAR(50) PRIMARY KEY COMMENT '언어명',
    `LANGUAGE_TYPE` ENUM('GENERAL', 'DB') DEFAULT 'GENERAL' NOT NULL COMMENT '언어 유형',
    `TIME_FACTOR` DECIMAL(3,1) DEFAULT 1.0 COMMENT '시간 제한 배수',
    `TIME_ADDITION` INT DEFAULT 0 COMMENT '시간 제한 추가(ms)',
    `MEMORY_FACTOR` DECIMAL(3,1) DEFAULT 1.0 COMMENT '메모리 제한 배수',
    `MEMORY_ADDITION` INT DEFAULT 0 COMMENT '메모리 제한 추가(MB)',
    
    INDEX `idx_language_type` (`LANGUAGE_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='언어별 채점 상수';

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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알고리즘 테스트케이스';

-- =============================================
-- 4. 알고리즘 제출 테이블
-- =============================================
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
    `AI_FEEDBACK_TYPE` ENUM('QUALITY', 'PERFORMANCE', 'STYLE', 'COMPREHENSIVE') DEFAULT 'COMPREHENSIVE' COMMENT 'AI 피드백 유형',
    `JUDGE_RESULT` ENUM('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE', 'PENDING') DEFAULT 'PENDING' COMMENT '채점 결과',
    `FOCUS_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '시선추적 집중도 점수 (0-100)',
    `AI_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT 'AI 코드 품질 점수 (0-100)',
    `TIME_EFFICIENCY_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '시간 효율성 점수 (0-100)',
    `FINAL_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '최종 종합 점수 (0-100)',
    `SCORE_WEIGHTS` JSON NULL COMMENT '점수 가중치 정보',
    `FOCUS_SESSION_ID` VARCHAR(255) NULL COMMENT '연결된 집중 추적 세션 ID',
    `EYETRACKED` TINYINT(1) DEFAULT 0 COMMENT '시선 추적 사용 여부',
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
    INDEX `idx_focus_session` (`FOCUS_SESSION_ID`),
    INDEX `idx_judge_result` (`JUDGE_RESULT`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알고리즘 제출 기록';

-- =============================================
-- 5. 집중 추적 세션 테이블
-- =============================================
CREATE TABLE `FOCUS_SESSIONS` (
    `SESSION_ID` VARCHAR(255) PRIMARY KEY COMMENT '세션 고유 식별자 (UUID)',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 ID',
    `SESSION_STATUS` ENUM('ACTIVE', 'COMPLETED', 'TERMINATED') DEFAULT 'ACTIVE' COMMENT '세션 진행 상태',
    `STARTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 시작 시각',
    `ENDED_AT` TIMESTAMP NULL COMMENT '세션 종료 시각',
    `REDIS_EXPIRES_AT` TIMESTAMP NULL COMMENT 'Redis 데이터 만료 시점',
    `REDIS_DATA_AVAILABLE` TINYINT(1) DEFAULT 1 COMMENT 'Redis 상세 데이터 존재 여부',
    `VIOLATION_COUNT` INT DEFAULT 0 COMMENT '감지된 부정행위 총 횟수',
    `SUMMARY_CREATED` TINYINT(1) DEFAULT 0 COMMENT '배치 요약 생성 완료 여부',
    
    FOREIGN KEY (`ALGO_PROBLEM_ID`) REFERENCES `ALGO_PROBLEMS`(`ALGO_PROBLEM_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    
    INDEX `idx_user_started` (`USER_ID`, `STARTED_AT` DESC),
    INDEX `idx_session_status` (`SESSION_STATUS`),
    INDEX `idx_summary_processing` (`SUMMARY_CREATED`, `ENDED_AT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='집중 추적 세션';

-- =============================================
-- 6. 집중도 요약 테이블
-- =============================================
CREATE TABLE `FOCUS_SUMMARY` (
    `SUMMARY_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '요약 고유 식별자',
    `SESSION_ID` VARCHAR(255) NOT NULL UNIQUE COMMENT '연결된 세션 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `TOTAL_EVENTS` INT DEFAULT 0 COMMENT '총 시선 추적 이벤트 수',
    `FOCUS_IN_PERCENTAGE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '전체 시간 대비 집중 비율',
    `TOTAL_VIOLATIONS` INT DEFAULT 0 COMMENT '총 부정행위 감지 횟수',
    `CRITICAL_VIOLATIONS` INT DEFAULT 0 COMMENT '치명적 부정행위 횟수',
    `FINAL_FOCUS_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '최종 집중도 점수',
    `FOCUS_GRADE` ENUM('EXCELLENT', 'GOOD', 'FAIR', 'POOR') NULL COMMENT '집중도 등급',
    `PROCESSED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '배치 처리 완료 시각',
    
    FOREIGN KEY (`SESSION_ID`) REFERENCES `FOCUS_SESSIONS`(`SESSION_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    
    UNIQUE INDEX `uk_session_id` (`SESSION_ID`),
    INDEX `idx_user_score` (`USER_ID`, `FINAL_FOCUS_SCORE` DESC),
    INDEX `idx_focus_grade` (`FOCUS_GRADE`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='집중도 배치 요약';

-- =============================================
-- 7. GitHub 커밋 테이블
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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GitHub 자동 커밋';

-- =============================================
-- 8. 부정행위 로그 테이블
-- =============================================
CREATE TABLE `VIOLATION_LOGS` (
    `VIOLATION_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '부정행위 고유 식별자',
    `SESSION_ID` VARCHAR(255) NOT NULL COMMENT '연결된 세션 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `VIOLATION_TYPE` ENUM('TAB_SWITCH', 'SCREEN_EXIT', 'FACE_MISSING', 'SUSPICIOUS_PATTERN') NOT NULL COMMENT '부정행위 유형',
    `SEVERITY` ENUM('HIGH', 'CRITICAL') NOT NULL COMMENT '부정행위 심각도',
    `OCCURRENCE_COUNT` INT DEFAULT 1 COMMENT '연속 발생 횟수',
    `AUTO_ACTION` ENUM('WARNING', 'TIME_REDUCTION', 'SESSION_TERMINATE') NOT NULL COMMENT '자동 처리 조치',
    `PENALTY_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '적용된 감점',
    `DETECTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '부정행위 감지 시각',
    `SESSION_TIME_OFFSET_SECONDS` INT NOT NULL COMMENT '세션 시작으로부터 오프셋(초)',
    
    FOREIGN KEY (`SESSION_ID`) REFERENCES `FOCUS_SESSIONS`(`SESSION_ID`) ON DELETE CASCADE,
    FOREIGN KEY (`USER_ID`) REFERENCES `USERS`(`USER_ID`) ON DELETE CASCADE,
    
    INDEX `idx_session_severity` (`SESSION_ID`, `SEVERITY`),
    INDEX `idx_user_violations` (`USER_ID`, `DETECTED_AT` DESC),
    INDEX `idx_violation_type` (`VIOLATION_TYPE`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='중대 부정행위 로그';

-- =============================================
-- AUTO_INCREMENT 시작값 설정
-- =============================================
ALTER TABLE `ALGO_PROBLEMS` AUTO_INCREMENT = 1;
ALTER TABLE `ALGO_TESTCASES` AUTO_INCREMENT = 1;
ALTER TABLE `ALGO_SUBMISSIONS` AUTO_INCREMENT = 1000;
ALTER TABLE `FOCUS_SUMMARY` AUTO_INCREMENT = 1;
ALTER TABLE `GITHUB_COMMITS` AUTO_INCREMENT = 1;
ALTER TABLE `VIOLATION_LOGS` AUTO_INCREMENT = 1;

-- =============================================
-- 언어별 상수 데이터 삽입 (제공된 표 반영 + LANGUAGE_TYPE)
-- =============================================
INSERT INTO `LANGUAGE_CONSTANTS` (`LANGUAGE_NAME`, `LANGUAGE_TYPE`, `TIME_FACTOR`, `TIME_ADDITION`, `MEMORY_FACTOR`, `MEMORY_ADDITION`) VALUES
-- C/C++ 계열 (기본값)
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
('Java 8 (OpenJDK)', 'GENERAL', 2.0, 1000, 2.0, 16),
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
('Scheme (Chicken)', 'GENERAL', 3.0, 2000, 2.0, 32),
('Scheme (Racket)', 'GENERAL', 3.0, 2000, 2.0, 16),
('Assembly (32bit)', 'GENERAL', 1.0, 0, 1.0, 0),
('Assembly (64bit)', 'GENERAL', 1.0, 0, 1.0, 0),
('Objective-C', 'GENERAL', 1.0, 0, 1.0, 0),
('Objective-C++', 'GENERAL', 1.0, 0, 1.0, 0),
('아희 (Aheui)', 'GENERAL', 1.0, 2000, 1.0, 64),
('아희 (Aheui) (Bok-sil)', 'GENERAL', 1.0, 2000, 1.0, 64),
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
) VALUES 
('두 수의 합', '두 정수를 입력받아 합을 출력하는 프로그램을 작성하시오.', 'BRONZE', 'BOJ', 'ALGORITHM', 1000, 128, '["수학", "구현"]'),
('피보나치 수', 'n번째 피보나치 수를 구하는 프로그램을 작성하시오.', 'SILVER', 'AI_GENERATED', 'ALGORITHM', 2000, 256, '["동적프로그래밍", "수학"]'),
('최단경로', '그래프에서 최단경로를 구하는 프로그램을 작성하시오.', 'GOLD', 'CUSTOM', 'ALGORITHM', 3000, 512, '["그래프", "다익스트라"]');

-- 샘플 테스트케이스
INSERT INTO `ALGO_TESTCASES` (`ALGO_PROBLEM_ID`, `INPUT_DATA`, `EXPECTED_OUTPUT`, `IS_SAMPLE`) VALUES
(1, '1 2', '3', 1),
(1, '5 7', '12', 1),
(1, '0 0', '0', 0),
(2, '1', '1', 1),
(2, '5', '5', 1),
(2, '10', '55', 0);

-- =============================================
-- 유용한 뷰 생성
-- =============================================

-- 사용자별 종합 통계 뷰
CREATE VIEW `V_USER_STATS` AS
SELECT 
    s.USER_ID,
    COUNT(s.ALGOSUBMISSION_ID) AS total_submissions,
    COUNT(CASE WHEN s.JUDGE_RESULT = 'AC' THEN 1 END) AS accepted_count,
    ROUND(AVG(s.FINAL_SCORE), 2) AS avg_final_score,
    ROUND(AVG(s.FOCUS_SCORE), 2) AS avg_focus_score,
    COUNT(DISTINCT s.ALGO_PROBLEM_ID) AS unique_problems
FROM ALGO_SUBMISSIONS s
GROUP BY s.USER_ID;

-- 문제별 통계 뷰
CREATE VIEW `V_PROBLEM_STATS` AS
SELECT 
    p.ALGO_PROBLEM_ID,
    p.ALGO_PROBLEM_TITLE,
    p.ALGO_PROBLEM_DIFFICULTY,
    COUNT(s.ALGOSUBMISSION_ID) AS total_attempts,
    COUNT(CASE WHEN s.JUDGE_RESULT = 'AC' THEN 1 END) AS success_count,
    ROUND(AVG(s.FINAL_SCORE), 2) AS avg_score
FROM ALGO_PROBLEMS p
LEFT JOIN ALGO_SUBMISSIONS s ON p.ALGO_PROBLEM_ID = s.ALGO_PROBLEM_ID
GROUP BY p.ALGO_PROBLEM_ID, p.ALGO_PROBLEM_TITLE, p.ALGO_PROBLEM_DIFFICULTY;

-- 최신 제출 조회 뷰
CREATE VIEW `V_RECENT_SUBMISSIONS` AS
SELECT 
    s.ALGOSUBMISSION_ID,
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
SELECT 
    p.ALGO_PROBLEM_DIFFICULTY,
    COUNT(DISTINCT p.ALGO_PROBLEM_ID) AS problem_count,
    COUNT(s.ALGOSUBMISSION_ID) AS total_submissions,
    COUNT(CASE WHEN s.JUDGE_RESULT = 'AC' THEN 1 END) AS success_submissions,
    ROUND(
        COUNT(CASE WHEN s.JUDGE_RESULT = 'AC' THEN 1 END) * 100.0 / 
        NULLIF(COUNT(s.ALGOSUBMISSION_ID), 0), 2
    ) AS success_rate,
    ROUND(AVG(s.FINAL_SCORE), 2) AS avg_score
FROM ALGO_PROBLEMS p
LEFT JOIN ALGO_SUBMISSIONS s ON p.ALGO_PROBLEM_ID = s.ALGO_PROBLEM_ID
GROUP BY p.ALGO_PROBLEM_DIFFICULTY
ORDER BY 
    CASE p.ALGO_PROBLEM_DIFFICULTY
        WHEN 'BRONZE' THEN 1
        WHEN 'SILVER' THEN 2
        WHEN 'GOLD' THEN 3
        WHEN 'PLATINUM' THEN 4
    END;

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
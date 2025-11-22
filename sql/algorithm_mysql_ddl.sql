-- =============================================
-- AUTO_INCREMENT 적용된 최종 알고리즘 도메인 MySQL DDL
-- =============================================

-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS `algorithm_platform` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `algorithm_platform`;

-- =============================================
-- 1. 알고리즘 문제 테이블 (AUTO_INCREMENT 적용)
-- =============================================
CREATE TABLE `ALGO_PROBLEMS` (
    `ALGO_PROBLEM_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '문제 고유 식별자',
    `ALGO_PROBLEM_TITLE` VARCHAR(255) NOT NULL COMMENT '문제 제목',
    `ALGO_PROBLEM_DESCRIPTION` TEXT NOT NULL COMMENT '문제 상세 설명',
    `ALGO_PROBLEM_DIFFICULTY` ENUM('BRONZE', 'SILVER', 'GOLD', 'PLATINUM') NOT NULL COMMENT '문제 난이도',
    `ALGO_PROBLEM_SOURCE` ENUM('AI_GENERATED', 'BOJ', 'CUSTOM') NOT NULL COMMENT '문제 생성 출처',
    `LANGUAGE` VARCHAR(50) DEFAULT 'ALL' COMMENT '지원 프로그래밍 언어',
    `TIMELIMIT` INT DEFAULT 1000 COMMENT '시간 제한(ms)',
    `MEMORYLIMIT` INT DEFAULT 256 COMMENT '메모리 제한(MB)',
    `ALGO_CREATER` BIGINT NULL COMMENT '문제 생성자 ID',
    `ALGO_CREATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    `ALGO_UPDATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    `ALGO_PROBLEM_TAGS` JSON NULL COMMENT '문제 태그 배열',
    `ALGO_PROBLEM_STATUS` TINYINT(1) DEFAULT 1 COMMENT '문제 활성화 상태',
    
    -- 인덱스
    INDEX `idx_difficulty_source` (`ALGO_PROBLEM_DIFFICULTY`, `ALGO_PROBLEM_SOURCE`),
    INDEX `idx_created_at` (`ALGO_CREATED_AT` DESC),
    INDEX `idx_problem_status` (`ALGO_PROBLEM_STATUS`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알고리즘 문제';

-- =============================================
-- 2. 알고리즘 테스트케이스 테이블 (AUTO_INCREMENT 적용)
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
-- 3. 알고리즘 제출 테이블 (AUTO_INCREMENT 적용 - 1000번부터 시작)
-- =============================================
CREATE TABLE `ALGO_SUBMISSIONS` (
    `ALGOSUBMISSION_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '제출 고유 식별자',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 고유 식별자',
    `USER_ID` BIGINT NOT NULL COMMENT '제출자 ID',
    `SOURCE_CODE` TEXT NOT NULL COMMENT '제출한 소스코드',
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
    
    -- 인덱스
    INDEX `idx_user_problem` (`USER_ID`, `ALGO_PROBLEM_ID`),
    INDEX `idx_final_score` (`FINAL_SCORE` DESC),
    INDEX `idx_submitted_at` (`SUBMITTED_AT` DESC),
    INDEX `idx_focus_session` (`FOCUS_SESSION_ID`),
    INDEX `idx_judge_result` (`JUDGE_RESULT`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알고리즘 제출 기록';

-- =============================================
-- 4. 집중 추적 세션 테이블 (UUID 사용 - AUTO_INCREMENT 없음)
-- =============================================
CREATE TABLE `FOCUS_SESSIONS` (
    `SESSION_ID` VARCHAR(255) PRIMARY KEY COMMENT '세션 고유 식별자 (UUID)',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    `ALGO_PROBLEM_ID` BIGINT NOT NULL COMMENT '문제 ID',
    
    -- 세션 기본 정보 (중복 제거: ALGO_SUBMISSIONS에 시간 정보 있음)
    `SESSION_STATUS` ENUM('ACTIVE', 'COMPLETED', 'TERMINATED') DEFAULT 'ACTIVE' COMMENT '세션 진행 상태',
    `STARTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '세션 시작 시각',
    `ENDED_AT` TIMESTAMP NULL COMMENT '세션 종료 시각',
    
    -- Redis 연동 정보만 (실시간 처리용)
    `REDIS_EXPIRES_AT` TIMESTAMP NULL COMMENT 'Redis 데이터 만료 시점',
    `REDIS_DATA_AVAILABLE` TINYINT(1) DEFAULT 1 COMMENT 'Redis 상세 데이터 존재 여부',
    
    -- 최소한의 실시간 정보만 (세부 점수는 FOCUS_SUMMARY에서)
    `VIOLATION_COUNT` INT DEFAULT 0 COMMENT '감지된 부정행위 총 횟수',
    
    -- 배치 처리 상태
    `SUMMARY_CREATED` TINYINT(1) DEFAULT 0 COMMENT '배치 요약 생성 완료 여부',
    
    -- 외래키
    FOREIGN KEY (`ALGO_PROBLEM_ID`) REFERENCES `ALGO_PROBLEMS`(`ALGO_PROBLEM_ID`) ON DELETE CASCADE,
    
    -- 인덱스
    INDEX `idx_user_started` (`USER_ID`, `STARTED_AT` DESC),
    INDEX `idx_session_status` (`SESSION_STATUS`),
    INDEX `idx_summary_processing` (`SUMMARY_CREATED`, `ENDED_AT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='집중 추적 세션';

-- =============================================
-- 5. 집중도 요약 테이블 (AUTO_INCREMENT 적용)
-- =============================================
CREATE TABLE `FOCUS_SUMMARY` (
    `SUMMARY_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '요약 고유 식별자',
    `SESSION_ID` VARCHAR(255) NOT NULL UNIQUE COMMENT '연결된 세션 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    
    -- 핵심 통계만 (중복 제거)
    `TOTAL_EVENTS` INT DEFAULT 0 COMMENT '총 시선 추적 이벤트 수',
    `FOCUS_IN_PERCENTAGE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '전체 시간 대비 집중 비율',
    
    -- 부정행위 요약 (최소화)
    `TOTAL_VIOLATIONS` INT DEFAULT 0 COMMENT '총 부정행위 감지 횟수',
    `CRITICAL_VIOLATIONS` INT DEFAULT 0 COMMENT '치명적 부정행위 횟수',
    
    -- 최종 결과 (핵심)
    `FINAL_FOCUS_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '최종 집중도 점수',
    `FOCUS_GRADE` ENUM('EXCELLENT', 'GOOD', 'FAIR', 'POOR') NULL COMMENT '집중도 등급',
    
    -- 처리 정보
    `PROCESSED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '배치 처리 완료 시각',
    
    -- 외래키
    FOREIGN KEY (`SESSION_ID`) REFERENCES `FOCUS_SESSIONS`(`SESSION_ID`) ON DELETE CASCADE,
    
    -- 인덱스
    UNIQUE INDEX `uk_session_id` (`SESSION_ID`),
    INDEX `idx_user_score` (`USER_ID`, `FINAL_FOCUS_SCORE` DESC),
    INDEX `idx_focus_grade` (`FOCUS_GRADE`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='집중도 배치 요약';

-- =============================================
-- 6. GitHub 커밋 테이블 (AUTO_INCREMENT 적용)
-- =============================================
CREATE TABLE `GITHUB_COMMITS` (
    `COMMIT_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '커밋 고유 식별자',
    `ALGOSUBMISSION_ID` BIGINT NOT NULL COMMENT '연결된 제출 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    
    -- GitHub 필수 정보만
    `GITHUB_USERNAME` VARCHAR(100) NOT NULL COMMENT 'GitHub 사용자명',
    `REPOSITORY_NAME` VARCHAR(100) NOT NULL COMMENT '리포지토리명',
    `BRANCH_NAME` VARCHAR(100) DEFAULT 'main' COMMENT '브랜치명',
    
    -- 커밋 설정 (최소화)
    `INCLUDE_AI_FEEDBACK` TINYINT(1) DEFAULT 1 COMMENT 'AI 피드백 포함 여부',
    `CUSTOM_COMMIT_MESSAGE` TEXT NULL COMMENT '사용자 지정 커밋 메시지',
    
    -- 커밋 결과
    `COMMIT_STATUS` ENUM('PENDING', 'COMPLETED', 'FAILED') DEFAULT 'PENDING' COMMENT '커밋 진행 상태',
    `COMMIT_URL` VARCHAR(500) NULL COMMENT 'GitHub 커밋 URL',
    
    -- 처리 정보 (최소화)
    `REQUESTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '커밋 요청 시각',
    `COMPLETED_AT` TIMESTAMP NULL COMMENT '커밋 완료 시각',
    `ERROR_MESSAGE` TEXT NULL COMMENT '커밋 실패 시 에러 메시지',
    
    -- 외래키
    FOREIGN KEY (`ALGOSUBMISSION_ID`) REFERENCES `ALGO_SUBMISSIONS`(`ALGOSUBMISSION_ID`) ON DELETE CASCADE,
    
    -- 인덱스
    INDEX `idx_submission` (`ALGOSUBMISSION_ID`),
    INDEX `idx_user_status` (`USER_ID`, `COMMIT_STATUS`),
    INDEX `idx_requested_at` (`REQUESTED_AT` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='GitHub 자동 커밋';

-- =============================================
-- 7. 부정행위 로그 테이블 (AUTO_INCREMENT 적용)
-- =============================================
CREATE TABLE `VIOLATION_LOGS` (
    `VIOLATION_ID` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '부정행위 고유 식별자',
    `SESSION_ID` VARCHAR(255) NOT NULL COMMENT '연결된 세션 ID',
    `USER_ID` BIGINT NOT NULL COMMENT '사용자 ID',
    
    -- 부정행위 핵심 정보만
    `VIOLATION_TYPE` ENUM('TAB_SWITCH', 'SCREEN_EXIT', 'FACE_MISSING', 'SUSPICIOUS_PATTERN') NOT NULL COMMENT '부정행위 유형',
    `SEVERITY` ENUM('HIGH', 'CRITICAL') NOT NULL COMMENT '부정행위 심각도 (HIGH 이상만)',
    `OCCURRENCE_COUNT` INT DEFAULT 1 COMMENT '연속 발생 횟수',
    
    -- 처리 결과 (핵심만)
    `AUTO_ACTION` ENUM('WARNING', 'TIME_REDUCTION', 'SESSION_TERMINATE') NOT NULL COMMENT '자동 처리 조치',
    `PENALTY_SCORE` DECIMAL(5,2) DEFAULT 0.00 COMMENT '적용된 감점',
    
    -- 시점 정보
    `DETECTED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '부정행위 감지 시각',
    `SESSION_TIME_OFFSET_SECONDS` INT NOT NULL COMMENT '세션 시작으로부터 오프셋(초)',
    
    -- 외래키
    FOREIGN KEY (`SESSION_ID`) REFERENCES `FOCUS_SESSIONS`(`SESSION_ID`) ON DELETE CASCADE,
    
    -- 인덱스
    INDEX `idx_session_severity` (`SESSION_ID`, `SEVERITY`),
    INDEX `idx_user_violations` (`USER_ID`, `DETECTED_AT` DESC),
    INDEX `idx_violation_type` (`VIOLATION_TYPE`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='중대 부정행위 로그';

-- =============================================
-- AUTO_INCREMENT 시작값 설정 (테이블 생성 후)
-- =============================================

-- 문제는 1번부터 시작 (자연스러운 번호)
ALTER TABLE `ALGO_PROBLEMS` AUTO_INCREMENT = 1;

-- 테스트케이스는 1번부터 시작
ALTER TABLE `ALGO_TESTCASES` AUTO_INCREMENT = 1;

-- 제출은 1000번부터 시작 (전문적인 느낌)
ALTER TABLE `ALGO_SUBMISSIONS` AUTO_INCREMENT = 1000;

-- 요약 ID는 1번부터 시작
ALTER TABLE `FOCUS_SUMMARY` AUTO_INCREMENT = 1;

-- 커밋 ID는 1번부터 시작
ALTER TABLE `GITHUB_COMMITS` AUTO_INCREMENT = 1;

-- 부정행위 로그는 1번부터 시작
ALTER TABLE `VIOLATION_LOGS` AUTO_INCREMENT = 1;

-- =============================================
-- 샘플 데이터 삽입
-- =============================================

-- 샘플 알고리즘 문제
INSERT INTO `ALGO_PROBLEMS` (
    `ALGO_PROBLEM_TITLE`, 
    `ALGO_PROBLEM_DESCRIPTION`, 
    `ALGO_PROBLEM_DIFFICULTY`, 
    `ALGO_PROBLEM_SOURCE`,
    `TIMELIMIT`,
    `MEMORYLIMIT`,
    `ALGO_PROBLEM_TAGS`
) VALUES 
('두 수의 합', '두 정수를 입력받아 합을 출력하는 프로그램을 작성하시오.', 'BRONZE', 'BOJ', 1000, 128, '["수학", "구현"]'),
('피보나치 수', 'n번째 피보나치 수를 구하는 프로그램을 작성하시오.', 'SILVER', 'AI_GENERATED', 2000, 256, '["동적프로그래밍", "수학"]'),
('최단경로', '그래프에서 최단경로를 구하는 프로그램을 작성하시오.', 'GOLD', 'CUSTOM', 3000, 512, '["그래프", "다익스트라"]');

-- 샘플 테스트케이스
INSERT INTO `ALGO_TESTCASES` (`ALGO_PROBLEM_ID`, `INPUT_DATA`, `EXPECTED_OUTPUT`, `IS_SAMPLE`) VALUES
(1, '1 2', '3', 1),
(1, '5 7', '12', 1),
(1, '0 0', '0', 0),
(2, '1', '1', 1),
(2, '5', '5', 1),
(2, '10', '55', 0);

-- =============================================
-- 유용한 뷰 생성 (최적화)
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

-- 최신 제출 조회 뷰 (AUTO_INCREMENT 활용)
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

-- InnoDB 설정 최적화
SET GLOBAL innodb_buffer_pool_size = 1073741824; -- 1GB (시스템에 맞게 조정)

-- 쿼리 로그 설정 (개발 환경에서만)
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;

-- AUTO_INCREMENT 증가값 설정 (기본값)
SET @@auto_increment_increment = 1;
SET @@auto_increment_offset = 1;

-- =============================================
-- 외래키 체크 및 완료
-- =============================================
SET FOREIGN_KEY_CHECKS = 1;

-- 트랜잭션 완료
COMMIT;

-- =============================================
-- 최종 확인 쿼리 (선택사항)
-- =============================================

-- 생성된 테이블 확인
SHOW TABLES;

-- AUTO_INCREMENT 설정 확인
SELECT 
    TABLE_NAME,
    AUTO_INCREMENT,
    TABLE_COMMENT
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'algorithm_platform'
AND AUTO_INCREMENT IS NOT NULL;

-- 외래키 관계 확인
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'algorithm_platform'
AND REFERENCED_TABLE_NAME IS NOT NULL;
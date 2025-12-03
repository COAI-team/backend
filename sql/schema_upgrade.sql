-- New tables for Codenose project upgrade

-- Table to store the history of each code analysis performed
CREATE TABLE `CODE_ANALYSIS_HISTORY` (
    `ANALYSIS_ID` VARCHAR(255) PRIMARY KEY,
    `USER_ID` BIGINT NOT NULL,
    `REPOSITORY_URL` VARCHAR(500),
    `FILE_PATH` VARCHAR(500),
    `ANALYSIS_TYPE` VARCHAR(50),
    `TONE_LEVEL` INT,
    `CUSTOM_REQUIREMENTS` TEXT,
    `ANALYSIS_RESULT` JSON,
    `AI_SCORE` INT,
    `CODE_SMELLS` JSON,
    `SUGGESTIONS` JSON,
    `CREATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`USER_ID`) REFERENCES `MEMBER`(`id`)
);

-- Table to track user-specific coding patterns and habits identified from analyses
CREATE TABLE `USER_CODE_PATTERNS` (
    `PATTERN_ID` VARCHAR(255) PRIMARY KEY,
    `USER_ID` BIGINT NOT NULL,
    `PATTERN_TYPE` VARCHAR(100),
    `FREQUENCY` INT DEFAULT 0,
    `LAST_DETECTED` TIMESTAMP,
    `IMPROVEMENT_STATUS` VARCHAR(50),
    FOREIGN KEY (`USER_ID`) REFERENCES `MEMBER`(`id`)
);

-- Note: The original document referenced USER(USER_ID).
-- Based on typical Spring Boot security and JPA setups, the user table is often named 'MEMBER'
-- and the primary key is 'id' of type BIGINT. This script assumes 'MEMBER' and 'id'.
-- Please adjust if your user table is named differently (e.g., 'USERS', 'APP_USER').

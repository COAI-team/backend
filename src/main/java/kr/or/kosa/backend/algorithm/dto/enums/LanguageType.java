package kr.or.kosa.backend.algorithm.dto.enums;

/**
 * 언어 유형 Enum
 * LANGUAGE_CONSTANTS 테이블의 LANGUAGE_TYPE 컬럼과 매핑
 */
public enum LanguageType {
    /**
     * 일반 프로그래밍 언어
     * C++, Java, Python 등 알고리즘 문제를 위한 언어
     */
    GENERAL,

    /**
     * 데이터베이스 언어
     * MySQL, PostgreSQL, SQLite 등 SQL 문제를 위한 언어
     */
    DB
}

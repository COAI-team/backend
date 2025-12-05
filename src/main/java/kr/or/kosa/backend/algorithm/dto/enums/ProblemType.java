package kr.or.kosa.backend.algorithm.dto.enums;

/**
 * 문제 유형 Enum
 * ALGO_PROBLEMS 테이블의 PROBLEM_TYPE 컬럼과 매핑
 */
public enum ProblemType {
    /**
     * 알고리즘 문제
     * C++, Java, Python 등의 일반 프로그래밍 언어로 풀이
     */
    ALGORITHM,

    /**
     * SQL 문제
     * MySQL, PostgreSQL 등의 DB 언어로 풀이
     * INIT_SCRIPT 필드에 초기화 스크립트 포함
     */
    SQL
}

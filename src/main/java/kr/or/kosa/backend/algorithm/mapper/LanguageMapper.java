package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LANGUAGES 테이블 Mapper 인터페이스
 * 언어별 채점 상수 CRUD 작업
 *
 * 변경사항 (2025-12-13):
 * - LANGUAGE_CONSTANTS → LANGUAGES 테이블 리팩토링
 * - languageId (INT, Judge0 API ID)를 Primary Key로 사용
 * - pistonLanguage 컬럼 추가
 */
@Mapper
public interface LanguageMapper {

    /**
     * 모든 언어 조회
     * 애플리케이션 시작 시 전체 데이터 로드하여 캐싱에 사용
     *
     * @return 전체 언어 리스트
     */
    List<LanguageDto> selectAll();

    /**
     * 언어 ID로 조회 (Judge0 API ID)
     * 채점 시 특정 언어의 제한 배수를 가져올 때 사용
     *
     * @param languageId 언어 ID (예: Python=100, Java=91)
     * @return 해당 언어 정보, 없으면 null
     */
    LanguageDto selectById(@Param("languageId") Integer languageId);

    /**
     * 언어명으로 조회
     * 표시용 언어명으로 검색할 때 사용
     *
     * @param languageName 언어명 (예: "Python", "Java")
     * @return 해당 언어 정보, 없으면 null
     */
    LanguageDto selectByName(@Param("languageName") String languageName);

    /**
     * Piston 언어명으로 조회
     * Piston API 연동 시 사용
     *
     * @param pistonLanguage Piston API 언어명 (예: "python", "java")
     * @return 해당 언어 정보, 없으면 null
     */
    LanguageDto selectByPistonLanguage(@Param("pistonLanguage") String pistonLanguage);

    /**
     * 언어 유형별 조회
     * 문제 타입에 따라 사용 가능한 언어 목록을 제공할 때 사용
     *
     * @param languageType 언어 유형 ("GENERAL" 또는 "DB")
     * @return 해당 유형의 언어 리스트
     */
    List<LanguageDto> selectByType(@Param("languageType") String languageType);

    /**
     * 언어 삽입
     * 새로운 언어 추가 시 사용 (관리자 기능)
     *
     * @param language 삽입할 언어 정보
     * @return 삽입된 행 수
     */
    int insert(LanguageDto language);

    /**
     * 언어 업데이트
     * 언어별 제한 규칙 변경 시 사용 (관리자 기능)
     *
     * @param language 업데이트할 언어 정보
     * @return 업데이트된 행 수
     */
    int update(LanguageDto language);

    /**
     * 언어 삭제
     * 언어 지원 중단 시 사용 (관리자 기능)
     * 주의: FK 제약 조건으로 인해 해당 언어로 제출된 기록이 있으면 삭제 불가
     *
     * @param languageId 삭제할 언어 ID
     * @return 삭제된 행 수
     */
    int deleteById(@Param("languageId") Integer languageId);
}

package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.LanguageConstant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LANGUAGE_CONSTANTS 테이블 Mapper 인터페이스
 * 언어별 채점 상수 CRUD 작업
 */
@Mapper
public interface LanguageConstantMapper {

    /**
     * 모든 언어 상수 조회
     * 애플리케이션 시작 시 전체 데이터 로드하여 캐싱에 사용
     * 
     * @return 전체 언어 상수 리스트
     */
    List<LanguageConstant> selectAll();

    /**
     * 언어명으로 조회
     * 채점 시 특정 언어의 제한 배수를 가져올 때 사용
     * 
     * @param languageName 언어명 (예: "Java 17", "Python 3")
     * @return 해당 언어의 상수 정보, 없으면 null
     */
    LanguageConstant selectByLanguageName(@Param("languageName") String languageName);

    /**
     * 언어 유형별 조회
     * 문제 타입에 따라 사용 가능한 언어 목록을 제공할 때 사용
     * 
     * @param languageType 언어 유형 ("GENERAL" 또는 "DB")
     * @return 해당 유형의 언어 상수 리스트
     */
    List<LanguageConstant> selectByLanguageType(@Param("languageType") String languageType);

    /**
     * 언어 상수 삽입
     * 새로운 언어 추가 시 사용 (관리자 기능)
     * 
     * @param languageConstant 삽입할 언어 상수
     * @return 삽입된 행 수
     */
    int insert(LanguageConstant languageConstant);

    /**
     * 언어 상수 업데이트
     * 언어별 제한 규칙 변경 시 사용 (관리자 기능)
     * 
     * @param languageConstant 업데이트할 언어 상수
     * @return 업데이트된 행 수
     */
    int update(LanguageConstant languageConstant);

    /**
     * 언어 상수 삭제
     * 언어 지원 중단 시 사용 (관리자 기능)
     * 
     * @param languageName 삭제할 언어명
     * @return 삭제된 행 수
     */
    int deleteByLanguageName(@Param("languageName") String languageName);
}

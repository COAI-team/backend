package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.dto.ProblemListResponseDto;
import kr.or.kosa.backend.algorithm.dto.ProblemStatisticsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlgorithmProblemMapper {

    // ===== V1 기본 필터링 쿼리 =====

    /**
     * 문제 목록 조회 (필터 포함) - V1
     *
     * @param offset     시작 위치
     * @param limit      조회 개수
     * @param difficulty 난이도 필터
     * @param source     출처 필터
     * @param keyword    검색어
     * @param topic      주제 필터
     * @return 문제 목록
     */
    List<AlgoProblem> selectProblemsWithFilter(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("difficulty") String difficulty,
            @Param("source") String source,
            @Param("keyword") String keyword,
            @Param("topic") String topic
    );

    /**
     * 전체 문제 개수 조회 (필터 포함) - V1
     *
     * @param difficulty 난이도 필터
     * @param source     출처 필터
     * @param keyword    검색어
     * @param topic      주제 필터
     * @return 필터링된 문제 개수
     */
    int countProblemsWithFilter(
            @Param("difficulty") String difficulty,
            @Param("source") String source,
            @Param("keyword") String keyword,
            @Param("topic") String topic
    );

    /**
     * 전체 문제 수 조회
     *
     * @return 전체 문제 수
     */
    int countAllProblems();

    // ===== V2 고급 필터링 쿼리 =====

    /**
     * 문제 목록 조회 V2 (고급 필터링 + 통계)
     *
     * @param userId     사용자 ID
     * @param offset     시작 위치
     * @param limit      조회 개수
     * @param difficulty 난이도 필터
     * @param source     출처 필터
     * @param language   언어 필터
     * @param keyword    검색어
     * @param topic      주제 필터
     * @param status     풀이 상태 필터
     * @param sortBy     정렬 기준
     * @return 문제 목록 (통계 포함)
     */
    List<ProblemListResponseDto> selectProblemList(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("difficulty") String difficulty,
            @Param("source") String source,
            @Param("language") String language,
            @Param("keyword") String keyword,
            @Param("topic") String topic,
            @Param("status") String status,
            @Param("sortBy") String sortBy
    );

    /**
     * 문제 목록 전체 개수 조회 - V2
     *
     * @param userId     사용자 ID
     * @param difficulty 난이도 필터
     * @param source     출처 필터
     * @param language   언어 필터
     * @param keyword    검색어
     * @param topic      주제 필터
     * @param status     풀이 상태 필터
     * @return 필터링된 문제 개수
     */
    int countProblemList(
            @Param("userId") Long userId,
            @Param("difficulty") String difficulty,
            @Param("source") String source,
            @Param("language") String language,
            @Param("keyword") String keyword,
            @Param("topic") String topic,
            @Param("status") String status
    );

    /**
     * 통계 정보 조회
     *
     * @param userId 사용자 ID
     * @return 통계 정보
     */
    ProblemStatisticsDto selectProblemStatistics(@Param("userId") Long userId);

    // ===== 문제 상세 조회 =====

    /**
     * 문제 상세 조회 (ID로)
     *
     * @param problemId 문제 ID
     * @return 문제 상세 정보
     */
    AlgoProblem selectProblemById(@Param("problemId") Long problemId);

    /**
     * 문제 존재 여부 확인
     *
     * @param problemId 문제 ID
     * @return 존재 여부
     */
    boolean existsProblemById(@Param("problemId") Long problemId);

    // ===== AI 생성 문제 INSERT =====

    /**
     * AI 문제 생성
     *
     * @param problem 문제 정보
     * @return 생성된 행 수
     */
    int insertProblem(AlgoProblem problem);

    // ===== 테스트케이스 관련 =====

    /**
     * 테스트케이스 생성
     *
     * @param testcase 테스트케이스 정보
     * @return 생성된 행 수
     */
    int insertTestcase(AlgoTestcase testcase);

    /**
     * 샘플 테스트케이스만 조회
     *
     * @param problemId 문제 ID
     * @return 샘플 테스트케이스 목록
     */
    List<AlgoTestcase> selectSampleTestCasesByProblemId(@Param("problemId") Long problemId);

    /**
     * 모든 테스트케이스 조회
     *
     * @param problemId 문제 ID
     * @return 전체 테스트케이스 목록
     */
    List<AlgoTestcase> selectTestCasesByProblemId(@Param("problemId") Long problemId);
}
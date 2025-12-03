package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlgorithmProblemMapper {

        /**
         * 전체 문제 목록 조회 (페이징)
         * 
         * @param offset 시작 위치 (0부터)
         * @param limit  조회 개수
         * @return 문제 목록
         */
        List<AlgoProblem> selectProblems(@Param("offset") int offset, @Param("limit") int limit);

        /**
         * 전체 문제 수 조회
         * 
         * @return 전체 문제 개수
         */
        int countAllProblems();

        /**
         * 문제 목록 조회 (필터 포함)
         *
         * @param offset     시작 위치 (0부터 시작)
         * @param limit      조회 개수
         * @param difficulty 난이도 필터 (nullable)
         * @param source     출처 필터 (nullable)
         * @param keyword    검색어 (nullable)
         * @return 문제 목록
         */
        List<AlgoProblem> selectProblemsWithFilter(
                        @Param("offset") int offset,
                        @Param("limit") int limit,
                        @Param("difficulty") String difficulty,
                        @Param("source") String source,
                        @Param("keyword") String keyword);

        /**
         * 전체 문제 개수 조회 (필터 포함)
         *
         * @param difficulty 난이도 필터 (nullable)
         * @param source     출처 필터 (nullable)
         * @param keyword    검색어 (nullable)
         * @return 필터링된 문제 개수
         */
        int countProblemsWithFilter(
                        @Param("difficulty") String difficulty,
                        @Param("source") String source,
                        @Param("keyword") String keyword);

        /**
         * 문제 상세 조회 (ID로)
         * 
         * @param problemId 문제 ID
         * @return 문제 정보 (없으면 null)
         */
        AlgoProblem selectProblemById(@Param("problemId") Long problemId);

        /**
         * 문제 존재 여부 확인
         * 
         * @param problemId 문제 ID
         * @return 존재 여부
         */
        boolean existsProblemById(@Param("problemId") Long problemId);

        // ===== AI 생성 문제 저장 메서드 =====
        /**
         * 문제 저장 (AI 생성 또는 수동 등록)
         * 
         * @param problem 문제 엔티티
         * @return 저장된 행 수
         */
        int insertProblem(AlgoProblem problem);

        /**
         * 테스트케이스 저장
         * 
         * @param testcase 테스트케이스 엔티티
         * @return 저장된 행 수
         */
        int insertTestcase(AlgoTestcase testcase);

        /**
         * 문제 ID로 테스트케이스 목록 조회
         * 
         * @param problemId 문제 ID
         * @return 테스트케이스 목록
         */
        List<AlgoTestcase> selectTestcasesByProblemId(@Param("problemId") Long problemId);

        /**
         * 샘플 테스트케이스 조회 (is_sample = true)
         */
        List<AlgoTestcase> selectSampleTestCasesByProblemId(@Param("problemId") Long problemId);

        /**
         * 모든 테스트케이스 조회
         */
        List<AlgoTestcase> selectTestCasesByProblemId(@Param("problemId") Long problemId);
}
package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 알고리즘 문제 MyBatis 매퍼 인터페이스
 * 기본 조회 기능만 구현
 */
@Mapper
public interface AlgorithmProblemMapper {

    /**
     * 전체 문제 목록 조회 (페이징)
     * @param offset 시작 위치 (0부터)
     * @param limit 조회 개수
     * @return 문제 목록
     */
    List<AlgoProblem> selectProblems(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 전체 문제 수 조회
     * @return 전체 문제 개수
     */
    int countAllProblems();

    /**
     * 문제 상세 조회 (ID로)
     * @param problemId 문제 ID
     * @return 문제 정보 (없으면 null)
     */
    AlgoProblem selectProblemById(@Param("problemId") Long problemId);

    /**
     * 문제 존재 여부 확인
     * @param problemId 문제 ID
     * @return 존재 여부
     */
    boolean existsProblemById(@Param("problemId") Long problemId);
}
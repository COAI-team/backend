package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.PoolProblemDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 문제 풀 MyBatis Mapper
 * 테이블: ALGO_PROBLEM_POOL
 */
@Mapper
public interface ProblemPoolMapper {

    /**
     * 조합별 문제 1개 조회 + 잠금 (SELECT FOR UPDATE)
     * 동시 요청 시 같은 문제 반환 방지
     *
     * @param difficulty 난이도
     * @param topic      알고리즘 주제
     * @param theme      스토리 테마
     * @return 풀 문제 (없으면 null)
     */
    PoolProblemDto findAndLockOne(
            @Param("difficulty") String difficulty,
            @Param("topic") String topic,
            @Param("theme") String theme
    );

    /**
     * 풀에서 문제 삭제 (소비 후)
     *
     * @param algoPoolId 풀 문제 ID
     * @return 삭제된 행 수
     */
    int deleteById(@Param("algoPoolId") Long algoPoolId);

    /**
     * 풀에 문제 추가
     *
     * @param poolProblem 풀 문제 DTO
     * @return 삽입된 행 수
     */
    int insert(PoolProblemDto poolProblem);

    /**
     * 조합별 현재 개수 조회
     *
     * @param difficulty 난이도
     * @param topic      알고리즘 주제
     * @param theme      스토리 테마
     * @return 해당 조합의 문제 개수
     */
    int countByCombination(
            @Param("difficulty") String difficulty,
            @Param("topic") String topic,
            @Param("theme") String theme
    );

    /**
     * 난이도별 현재 개수 조회
     *
     * @param difficulty 난이도
     * @return 해당 난이도의 문제 개수
     */
    int countByDifficulty(@Param("difficulty") String difficulty);

    /**
     * 전체 풀 개수 조회
     *
     * @return 전체 문제 개수
     */
    int countAll();

    /**
     * 난이도별 문제 목록 조회 (랜덤 순서)
     * 데일리 미션에서 사용
     *
     * @param difficulty 난이도
     * @param limit      최대 개수
     * @return 문제 목록
     */
    List<PoolProblemDto> findByDifficultyRandomly(
            @Param("difficulty") String difficulty,
            @Param("limit") int limit
    );

    /**
     * 난이도만으로 문제 1개 조회 + 잠금 (SELECT FOR UPDATE)
     * 데일리 미션에서 사용 - 랜덤으로 토픽/테마 선택
     *
     * @param difficulty 난이도
     * @return 풀 문제 (없으면 null)
     */
    PoolProblemDto findAndLockOneByDifficulty(@Param("difficulty") String difficulty);

    /**
     * 부족한 조합 목록 조회 (스케줄러에서 사용)
     * 조합당 목표 개수보다 적은 조합들 반환
     *
     * @param targetCount 목표 개수
     * @return 부족한 조합 목록 (difficulty, topic, theme, currentCount)
     */
    List<java.util.Map<String, Object>> findDeficientCombinations(@Param("targetCount") int targetCount);
}

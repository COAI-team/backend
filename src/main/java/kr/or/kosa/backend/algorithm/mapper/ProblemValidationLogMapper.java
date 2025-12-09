package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.ProblemValidationLogDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 문제 생성 검증 로그 Mapper
 * 개발자용 품질 검증 데이터 관리
 */
@Mapper
public interface ProblemValidationLogMapper {

    /**
     * 검증 로그 저장
     * @param validationLog 검증 로그 DTO
     * @return 저장된 행 수
     */
    int insertValidationLog(ProblemValidationLogDto validationLog);

    /**
     * 검증 로그 업데이트
     * @param validationLog 검증 로그 DTO
     * @return 업데이트된 행 수
     */
    int updateValidationLog(ProblemValidationLogDto validationLog);

    /**
     * 문제 ID로 검증 로그 조회
     * @param algoProblemId 문제 ID
     * @return 검증 로그 (없으면 null)
     */
    ProblemValidationLogDto selectByProblemId(@Param("algoProblemId") Long algoProblemId);

    /**
     * 검증 ID로 조회
     * @param validationId 검증 로그 ID
     * @return 검증 로그
     */
    ProblemValidationLogDto selectById(@Param("validationId") Long validationId);

    /**
     * 검증 상태별 로그 조회
     * @param status 검증 상태 (PENDING, PASSED, FAILED, CORRECTED)
     * @param limit 조회 개수
     * @return 검증 로그 목록
     */
    List<ProblemValidationLogDto> selectByStatus(
            @Param("status") String status,
            @Param("limit") int limit
    );

    /**
     * 검증 실패 로그 조회 (Self-Correction 대상)
     * @param maxAttempts 최대 재시도 횟수
     * @return 재시도 가능한 실패 로그 목록
     */
    List<ProblemValidationLogDto> selectFailedForCorrection(@Param("maxAttempts") int maxAttempts);

    /**
     * 검증 상태 업데이트
     * @param validationId 검증 로그 ID
     * @param status 새로운 상태
     * @return 업데이트된 행 수
     */
    int updateStatus(
            @Param("validationId") Long validationId,
            @Param("status") String status
    );

    /**
     * 코드 실행 결과 업데이트
     * @param validationId 검증 로그 ID
     * @param optimalCodeResult 최적 코드 결과
     * @param naiveCodeResult 비효율 코드 결과
     * @param optimalExecutionTime 최적 코드 실행 시간
     * @param naiveExecutionTime 비효율 코드 실행 시간
     * @param timeRatio 시간 비율
     * @param timeRatioValid 시간 비율 검증 통과 여부
     * @return 업데이트된 행 수
     */
    int updateExecutionResults(
            @Param("validationId") Long validationId,
            @Param("optimalCodeResult") String optimalCodeResult,
            @Param("naiveCodeResult") String naiveCodeResult,
            @Param("optimalExecutionTime") Integer optimalExecutionTime,
            @Param("naiveExecutionTime") Integer naiveExecutionTime,
            @Param("timeRatio") Double timeRatio,
            @Param("timeRatioValid") Boolean timeRatioValid
    );

    /**
     * 유사도 검사 결과 업데이트
     * @param validationId 검증 로그 ID
     * @param similarityScore 유사도 점수
     * @param similarityValid 유사도 검증 통과 여부
     * @return 업데이트된 행 수
     */
    int updateSimilarityResults(
            @Param("validationId") Long validationId,
            @Param("similarityScore") Double similarityScore,
            @Param("similarityValid") Boolean similarityValid
    );

    /**
     * Self-Correction 시도 횟수 증가
     * @param validationId 검증 로그 ID
     * @return 업데이트된 행 수
     */
    int incrementCorrectionAttempts(@Param("validationId") Long validationId);
}

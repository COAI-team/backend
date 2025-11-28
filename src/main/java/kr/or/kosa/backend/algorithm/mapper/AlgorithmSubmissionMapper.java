package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 알고리즘 제출 관련 MyBatis 매퍼
 */
@Mapper
public interface AlgorithmSubmissionMapper {

    /**
     * 제출 저장
     * @param submission 제출 정보
     * @return 영향받은 행 수
     */
    int insertSubmission(AlgoSubmission submission);

    /**
     * 제출 ID로 조회
     * @param submissionId 제출 ID
     * @return 제출 정보
     */
    AlgoSubmission selectSubmissionById(@Param("submissionId") Long submissionId);

    /**
     * 제출 정보 업데이트 (채점 결과 등)
     * @param submission 제출 정보
     * @return 영향받은 행 수
     */
    int updateSubmission(AlgoSubmission submission);

    /**
     * 사용자별 제출 목록 조회 (페이징)
     * @param userId 사용자 ID
     * @param offset 시작 위치
     * @param limit 조회 수
     * @return 제출 목록
     */
    List<AlgoSubmission> selectSubmissionsByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 사용자별 제출 총 개수
     * @param userId 사용자 ID
     * @return 총 개수
     */
    int countSubmissionsByUserId(@Param("userId") Long userId);

    /**
     * 문제별 제출 목록 조회 (공개된 것만)
     * @param problemId 문제 ID
     * @param offset 시작 위치
     * @param limit 조회 수
     * @return 제출 목록
     */
    List<AlgoSubmission> selectPublicSubmissionsByProblemId(
            @Param("problemId") Long problemId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 특정 사용자의 특정 문제 최고 점수 제출 조회
     * @param userId 사용자 ID
     * @param problemId 문제 ID
     * @return 최고 점수 제출
     */
    AlgoSubmission selectBestSubmissionByUserAndProblem(
            @Param("userId") Long userId,
            @Param("problemId") Long problemId
    );

    /**
     * AI 피드백 업데이트
     * @param submissionId 제출 ID
     * @param aiFeedback AI 피드백 내용
     * @param aiScore AI 점수
     * @param finalScore 최종 점수
     * @return 영향받은 행 수
     */
    int updateAiFeedback(
            @Param("submissionId") Long submissionId,
            @Param("aiFeedback") String aiFeedback,
            @Param("aiScore") java.math.BigDecimal aiScore,
            @Param("finalScore") java.math.BigDecimal finalScore
    );

    /**
     * 공유 상태 업데이트
     * @param submissionId 제출 ID
     * @param isShared 공유 여부
     * @return 영향받은 행 수
     */
    int updateSharingStatus(
            @Param("submissionId") Long submissionId,
            @Param("isShared") Boolean isShared
    );
}
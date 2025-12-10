package kr.or.kosa.backend.codenose.mapper;

import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 분석 매퍼 (AnalysisMapper)
 * 
 * 역할:
 * MyBatis를 사용하여 데이터베이스와 상호작용합니다.
 * GitHub 파일 저장, 분석 결과 조회/저장, 사용자 코드 패턴 관리 기능을 제공합니다.
 */
@Mapper
public interface AnalysisMapper {

        // ========== GitHub 파일 관련 (GITHUB_FILES 테이블) ==========
        /**
         * GitHub 파일 내용을 DB에 저장
         * 
         * @param fileData 저장할 파일 정보 (경로, 내용, 메타데이터 등)
         */
        void saveFileContent(GithubFileDTO fileData);

        /**
         * fileId로 GitHub 파일 조회
         * 
         * @param fileId 파일 고유 ID (UUID)
         * @return 파일 정보 DTO
         */
        GithubFileDTO findFileById(String fileId);

        /**
         * repositoryUrl + filePath로 최신 GitHub 파일 조회
         * 
         * 특정 레포지토리의 특정 경로에 있는 최신 파일 정보를 가져옵니다.
         * 
         * @param repositoryUrl 레포지토리 URL
         * @param filePath      파일 경로
         * @return 파일 정보 DTO
         */
        GithubFileDTO findLatestFileContent(
                        @Param("repositoryUrl") String repositoryUrl,
                        @Param("filePath") String filePath);

        // ========== 코드 분석 결과 관련 (CODE_ANALYSIS_HISTORY 테이블) ==========
        /**
         * 코드 분석 결과 저장
         * 
         * AI가 분석한 결과를 이력 테이블에 저장합니다.
         * 
         * @param history 분석 결과 DTO
         */
        void saveCodeResult(CodeResultDTO history);

        /**
         * 사용자별 코드 분석 결과 목록 조회
         * 
         * @param userId 사용자 ID
         * @return 분석 결과 리스트
         */
        List<CodeResultDTO> findCodeResultByUserId(Long userId);

        /**
         * analysisId로 특정 분석 결과 조회
         * 
         * @param analysisId 분석 ID (UUID)
         * @return 분석 결과 DTO
         */
        CodeResultDTO findCodeResultById(String analysisId);

        /**
         * 메타데이터가 없는 분석 결과 조회
         * 
         * RAG 벡터 DB에 아직 임베딩되지 않은(메타데이터가 없는) 분석 결과를 가져옵니다.
         * 백그라운드 작업 등에서 사용됩니다.
         * 
         * @return 분석 결과 리스트
         */
        List<CodeResultDTO> findAnalysisWithoutMetadata();

        /**
         * 분석 결과 메타데이터 업데이트
         * 
         * RAG 처리가 완료된 후 메타데이터(임베딩 상태 등)를 업데이트합니다.
         * 
         * @param analysisId 분석 ID
         * @param metadata   업데이트할 메타데이터 (JSON)
         */
        void updateAnalysisMetadata(@Param("analysisId") String analysisId, @Param("metadata") String metadata);

        // ========== 사용자 코드 패턴 관련 ==========
        /**
         * 사용자 코드 패턴 저장
         * 
         * 분석 결과에서 도출된 사용자의 코딩 습관/패턴을 저장합니다.
         * 
         * @param pattern 코드 패턴 DTO
         */
        void saveUserCodePattern(UserCodePatternDTO pattern);

        /**
         * 사용자별 특정 패턴 조회
         * 
         * @param userId      사용자 ID
         * @param patternType 패턴 유형 (예: "Dead Code", "Naming Convention")
         * @return 코드 패턴 DTO
         */
        UserCodePatternDTO findUserCodePattern(
                        @Param("userId") Long userId,
                        @Param("patternType") String patternType);

        /**
         * 사용자 코드 패턴 업데이트
         * 
         * 기존 패턴 정보(빈도수 등)를 갱신합니다.
         * 
         * @param pattern 업데이트할 코드 패턴 DTO
         */
        void updateUserCodePattern(UserCodePatternDTO pattern);

        /**
         * 사용자별 모든 패턴 조회
         * 
         * @param userId 사용자 ID
         * @return 코드 패턴 리스트
         */
        List<UserCodePatternDTO> findAllPatternsByUserId(Long userId);

        /**
         * 사용자별 특정 기간 동안의 코드 분석 결과 조회
         * 
         * 통계나 마이페이지 리포트 생성 시 사용됩니다.
         * 
         * @param userId    사용자 ID
         * @param startDate 시작 날짜
         * @param endDate   종료 날짜
         * @return 기간 내 분석 결과 리스트
         */
        List<CodeResultDTO> findCodeResultsByUserIdAndDateRange(
                        @Param("userId") Long userId,
                        @Param("startDate") java.sql.Timestamp startDate,
                        @Param("endDate") java.sql.Timestamp endDate);
}
package kr.or.kosa.backend.codenose.mapper;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.GithubFileDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisMapper {

        // ========== GitHub 파일 관련 (GITHUB_FILES 테이블) ==========
        /**
         * GitHub 파일 내용을 DB에 저장
         */
        void saveFileContent(GithubFileDTO fileData);

        /**
         * fileId로 GitHub 파일 조회
         */
        GithubFileDTO findFileById(String fileId);

        /**
         * repositoryUrl + filePath로 최신 GitHub 파일 조회
         */
        GithubFileDTO findLatestFileContent(
                        @Param("repositoryUrl") String repositoryUrl,
                        @Param("filePath") String filePath);

        // ========== 코드 분석 결과 관련 (CODE_ANALYSIS_HISTORY 테이블) ==========
        /**
         * 코드 분석 결과 저장
         */
        void saveCodeResult(CodeResultDTO history);

        /**
         * 사용자별 코드 분석 결과 조회
         */
        List<CodeResultDTO> findCodeResultByUserId(Long userId);

        /**
         * analysisId로 분석 결과 조회
         */
        CodeResultDTO findCodeResultById(String analysisId);

        /**
         * 메타데이터가 없는 분석 결과 조회
         */
        List<CodeResultDTO> findAnalysisWithoutMetadata();

        /**
         * 분석 결과 메타데이터 업데이트
         */
        void updateAnalysisMetadata(@Param("analysisId") String analysisId, @Param("metadata") String metadata);

        // ========== 사용자 코드 패턴 관련 ==========
        /**
         * 사용자 코드 패턴 저장
         */
        void saveUserCodePattern(UserCodePatternDTO pattern);

        /**
         * 사용자별 특정 패턴 조회
         */
        UserCodePatternDTO findUserCodePattern(
                        @Param("userId") Long userId,
                        @Param("patternType") String patternType);

        /**
         * 사용자 코드 패턴 업데이트
         */
        void updateUserCodePattern(UserCodePatternDTO pattern);

        /**
         * 사용자별 모든 패턴 조회
         */
        List<UserCodePatternDTO> findAllPatternsByUserId(Long userId);

        /**
         * 사용자별 특정 기간 동안의 코드 분석 결과 조회
         */
        List<CodeResultDTO> findCodeResultsByUserIdAndDateRange(
                        @Param("userId") Long userId,
                        @Param("startDate") java.sql.Timestamp startDate,
                        @Param("endDate") java.sql.Timestamp endDate);
}
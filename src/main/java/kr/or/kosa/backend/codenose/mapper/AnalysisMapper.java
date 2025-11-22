package kr.or.kosa.backend.codenose.mapper;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalysisMapper {
    void saveCodeResult(CodeResultDTO history);
    List<CodeResultDTO> findCodResultyByUserId(Long userId);
    void saveUserCodePattern(UserCodePatternDTO pattern);
    UserCodePatternDTO findUserCodePattern(Long userId, String patternType);
    void updateUserCodePattern(UserCodePatternDTO pattern);
    List<UserCodePatternDTO> findAllPatternsByUserId(Long userId);

    // 파일 내용 임시 저장 (분석 전)
    void saveFileContent(CodeResultDTO fileData);

    // repositoryUrl + filePath로 최신 파일 내용 조회
    CodeResultDTO findLatestFileContent(String repositoryUrl, String filePath);

    // 분석 결과로 업데이트
    void updateAnalysisResult(CodeResultDTO result);
}

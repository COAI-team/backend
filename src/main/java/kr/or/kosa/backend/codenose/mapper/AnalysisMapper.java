package kr.or.kosa.backend.codenose.mapper;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalysisMapper {

    // XML: <insert id="saveAnalysisHistory">
    void saveAnalysisHistory(CodeResultDTO history);

    // XML: <select id="findAnalysisHistoryByUserId">
    List<CodeResultDTO> findAnalysisHistoryByUserId(Long userId);

    // XML: <insert id="saveUserCodePattern">
    void saveUserCodePattern(UserCodePatternDTO pattern);

    // XML: <select id="findUserCodePattern">
    UserCodePatternDTO findUserCodePattern(Long userId, String patternType);

    // XML: <update id="updateUserCodePattern">
    void updateUserCodePattern(UserCodePatternDTO pattern);

    // XML: <select id="findAllPatternsByUserId">
    List<UserCodePatternDTO> findAllPatternsByUserId(Long userId);

    // XML: <insert id="saveFileContent">
    void saveFileContent(CodeResultDTO fileData);

    // XML: <select id="findLatestFileContent">
    CodeResultDTO findLatestFileContent(String repositoryUrl, String filePath);

    // XML: <update id="updateAnalysisResult">
    void updateAnalysisResult(CodeResultDTO result);
}
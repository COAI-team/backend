package kr.or.kosa.backend.codenose.mapper;

import kr.or.kosa.backend.codenose.dto.CodeAnalysisHistoryDto;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalysisMapper {
    void saveAnalysisHistory(CodeAnalysisHistoryDto history);
    List<CodeAnalysisHistoryDto> findAnalysisHistoryByUserId(Long userId);
    void saveUserCodePattern(UserCodePatternDto pattern);
    UserCodePatternDto findUserCodePattern(Long userId, String patternType);
    void updateUserCodePattern(UserCodePatternDto pattern);
    List<UserCodePatternDto> findAllPatternsByUserId(Long userId);
}

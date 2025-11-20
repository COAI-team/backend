package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.CodeAnalysisHistoryDto;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDto;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightsService {

    private final AnalysisMapper analysisMapper;

    public List<CodeAnalysisHistoryDto> getAnalysisHistory(Long userId) {
        return analysisMapper.findAnalysisHistoryByUserId(userId);
    }
    
    public List<UserCodePatternDto> getUserCodePatterns(Long userId) {
        return analysisMapper.findAllPatternsByUserId(userId);
    }
}

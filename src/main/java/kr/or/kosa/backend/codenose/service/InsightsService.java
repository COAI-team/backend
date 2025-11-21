package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightsService {

    private final AnalysisMapper analysisMapper;

    public List<CodeResultDTO> getCodeResult(Long userId) {
        return analysisMapper.findCodResultyByUserId(userId);
    }

    public List<UserCodePatternDTO> getUserCodePatterns(Long userId) {
        return analysisMapper.findAllPatternsByUserId(userId);
    }
}

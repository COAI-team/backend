package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDto {
    private String code;
    private List<String> analysisTypes; // e.g., "code_smell", "design_pattern"
    private int toneLevel; // 1 (gentle) to 5 (strict)
    private String customRequirements;
    
    // Fields from the previous steps that we'll need
    private String repositoryUrl;
    private String filePath;
    private Long userId; // Assuming we get this from the authenticated user context
}

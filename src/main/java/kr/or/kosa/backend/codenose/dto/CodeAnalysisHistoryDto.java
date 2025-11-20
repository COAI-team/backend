package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeAnalysisHistoryDto {
    private String analysisId;
    private Long userId;
    private String repositoryUrl;
    private String filePath;
    private String analysisType;
    private int toneLevel;
    private String customRequirements;
    private String analysisResult; // Storing JSON as String
    private int aiScore;
    private String codeSmells; // Storing JSON as String
    private String suggestions; // Storing JSON as String
    private Timestamp createdAt;
}

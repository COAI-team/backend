package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.*;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeResultDTO {
    private String analysisId; // PK: 분석 ID (UUID)
    private Long userId; // FK: 사용자 ID -> USER.user_id
    private String repositoryUrl; // GitHub 레포지토리 URL
    private String filePath; // 분석한 파일 경로
    private String analysisType; // 분석 타입 (code_smell, design_pattern 등)
    private int toneLevel; // 분석 톤 레벨 (1: gentle ~ 5: strict)
    private String customRequirements; // 사용자 커스텀 요구사항
    private String analysisResult; // AI 분석 결과 전체 (JSON 문자열)
    private int aiScore; // AI 평가 점수 (0-100)
    private String codeSmells; // 발견된 코드 스멜 목록 (JSON 문자열)
    private String suggestions; // 개선 제안 목록 (JSON 문자열)
    private String metadata;
    private String relatedAnalysisIds; // 코드 메타데이터 (JSON 문자열)
    private Timestamp createdAt; // 분석 생성 시간
}
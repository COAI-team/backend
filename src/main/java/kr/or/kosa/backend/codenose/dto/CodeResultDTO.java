package kr.or.kosa.backend.codenose.dto;

import lombok.*;

import java.sql.Timestamp;

/**
 * 코드 분석 결과 DTO (CodeResultDTO)
 * 
 * 역할:
 * `CODE_ANALYSIS_HISTORY` 테이블과 매핑되어 코드 분석 결과를 저장하고 조회하는 데 사용됩니다.
 * 분석된 코드의 메타데이터, AI 분석 결과 원본(JSON), 발견된 코드 스멜, 제안 사항 등을 오롯이 담고 있습니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeResultDTO {
    private String analysisId; // PK: 분석 ID (UUID, 고유 식별자)
    private Long userId; // FK: 분석을 요청한 사용자 ID
    private String repositoryUrl; // GitHub 레포지토리 URL (해당되는 경우)
    private String filePath; // 분석된 파일의 경로
    private String analysisType; // 분석 유형 (code_smell, security 등)
    private int toneLevel; // 분석에 적용된 톤 레벨
    private String customRequirements; // 사용자 지정 커스텀 요구사항
    private String analysisResult; // AI가 생성한 분석 결과 전체 (JSON 형식의 문자열)
    private int aiScore; // AI가 평가한 코드 점수 (0~100)
    private String codeSmells; // 발견된 주요 코드 스멜 목록 (JSON)
    private String suggestions; // 개선 제안 사항 목록 (JSON)
    private String metadata; // RAG 벡터 DB 메타데이터
    private String relatedAnalysisIds; // 데이터 관리를 위한 추가 필드
    private Timestamp createdAt; // 생성 일시
}
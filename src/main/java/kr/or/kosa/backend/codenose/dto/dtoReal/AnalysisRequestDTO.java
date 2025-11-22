package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 분석 요청 DTO
 * API 요청 데이터 전송용 (데이터베이스 테이블 매핑 없음)
 *
 * 클라이언트에서 코드 분석을 요청할 때 사용하는 DTO
 * - 분석할 코드, 분석 타입, 톤 레벨, 커스텀 요구사항 등을 포함
 * - 서버에서 받은 후 CODE_ANALYSIS_HISTORY로 변환되어 저장됨
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDTO {
    private String code;                    // 분석할 코드 내용
    private List<String> analysisTypes;     // 분석 타입 리스트 (code_smell, design_pattern 등)
    private int toneLevel;                  // 분석 톤 레벨 (1: gentle ~ 5: strict)
    private String customRequirements;      // 사용자 커스텀 요구사항

    // 이전 단계에서 필요한 필드들
    private String repositoryUrl;           // GitHub 레포지토리 URL
    private String filePath;                // 분석할 파일 경로
    private Long userId;                    // 사용자 ID (인증된 사용자 컨텍스트에서 가져옴)
}

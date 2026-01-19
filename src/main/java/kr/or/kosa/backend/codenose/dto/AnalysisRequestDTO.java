package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 분석 요청 DTO (AnalysisRequestDTO)
 * 
 * 역할:
 * 클라이언트로부터 코드 분석 요청을 받을 때 사용하는 데이터 전송 객체입니다.
 * 이 객체는 데이터베이스 테이블과 직접 매핑되지 않습니다.
 * 
 * 사용 흐름:
 * 1. 클라이언트(Frontend) -> 컨트롤러: 분석 요청 (JSON)
 * 2. 컨트롤러: 이 DTO로 파싱
 * 3. 서비스: 이 DTO의 정보를 바탕으로 분석 실행 -> CodeResultDTO로 변환하여 DB 저장
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDTO {
    // --- 클라이언트 입력 필드 ---
    private String code; // 분석 대상 소스 코드 본문
    private List<String> analysisTypes; // 실행할 분석 유형 (예: code_smell, design_pattern, security)
    private int toneLevel; // 분석 톤 앤 매너 레벨 (1: 부드러움 ~ 5: 엄격함)
    private String customRequirements; // 사용자가 직접 입력한 추가 요구사항

    // --- 내부 처리 및 컨텍스트 필드 ---
    private String repositoryUrl; // (선택) GitHub 레포지토리 URL
    private String filePath; // (선택) 파일 경로
    private String fileContent; // (선택) 파일 내용 (code 필드가 비어있을 경우 사용)
    private String analysisId; // (선택) 기존 분석 ID (재분석 시)
    private Long userId; // 요청한 사용자 ID (인증 토큰에서 추출)
}

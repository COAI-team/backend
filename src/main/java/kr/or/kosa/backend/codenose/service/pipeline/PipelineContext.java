package kr.or.kosa.backend.codenose.service.pipeline;

import lombok.Builder;
import lombok.Data;

/**
 * 파이프라인 컨텍스트 (PipelineContext)
 * 
 * 역할:
 * 파이프라인의 각 단계(Step) 간에 데이터를 전달하는 DTO 객체입니다.
 * 원본 코드, 사용자 컨텍스트, 중간 결과물(스타일, 최적화 로직), 최종 결과를 담습니다.
 */
@Data
@Builder
public class PipelineContext {
    private String originalCode; // 분석할 원본 코드
    private String userContext; // 사용자 이력 및 컨텍스트 (RAG 등에서 주입)
    private String styleRules; // 추출된 스타일 규칙 (1단계 결과)
    private String optimizedLogic; // 최적화된 로직 코드 (2단계 결과)
    private String finalResult; // 최종 생성된 코드 (3단계 결과)
}

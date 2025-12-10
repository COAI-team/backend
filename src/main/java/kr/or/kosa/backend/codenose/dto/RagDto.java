package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 관련 DTO 모음 (RagDto)
 * 
 * 역할:
 * Vector Store에 데이터를 삽입(Ingest)하거나, RAG 기반 피드백을 요청/응답할 때 사용하는
 * 내부 클래스(Inner Class)들을 그룹화한 클래스입니다.
 */
public class RagDto {

    /**
     * RAG 데이터 삽입 요청 (IngestRequest)
     * 벡터 DB에 저장될 코드 정보와 메타데이터를 담습니다.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        private String userId; // 소유자 ID
        private String code; // 코드 본문
        private String analysis; // 분석 결과 텍스트
        private String language; // 프로그래밍 언어 (예: "java")
        private String problemTitle; // 문제 제목 또는 파일명

        // --- 추가 메타데이터 ---
        private String majorChanges; // 주요 변경 사항 요약
        private String desiredAnalysis; // 사용자가 원했던 분석 방향
        private String analysisId; // 원본 분석 ID (추적용)
    }

    /**
     * 피드백 요청 (FeedbackRequest)
     * 챗봇에게 코드 관련 질문을 할 때 사용됩니다.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRequest {
        private String userId; // 사용자 ID (컨텍스트 검색용)
        private String question; // 질문 내용
    }

    /**
     * 피드백 응답 (FeedbackResponse)
     * RAG를 통해 생성된 AI의 답변을 담습니다.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackResponse {
        private String answer; // AI 답변 내용
    }
}

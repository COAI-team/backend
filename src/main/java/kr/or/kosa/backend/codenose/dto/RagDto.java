package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class RagDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        private String userId;
        private String code;
        private String analysis;
        private String language;
        private String problemTitle;
        // New metadata fields
        private String majorChanges;
        private String desiredAnalysis;
        private String analysisId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRequest {
        private String userId;
        private String question;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackResponse {
        private String answer;
    }
}

package kr.or.kosa.backend.admin.dto.response;

import java.time.LocalDateTime;

public record AlgoBoardDetailResponseDto(
    long algoSubmissionId,
    long userId,
    String userNickName,
    String algoProblemTitle,
    String algoProblemDescription,
    String algoProblemDifficulty,
    String problemType,
    String sourceCode,
    String language,
    String aiFeedback,
    String aiFeedbackType,
    String solveMode,
    LocalDateTime submittedAt
) {}

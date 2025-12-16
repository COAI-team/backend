package kr.or.kosa.backend.admin.dto.response;

public record AdminFreeBoardDetailResponseDto(
    Long freeboardId,
    Long userId,
    String userNickNae,
    String freeboardTitle,
    String freeboardContent,
    String freeBoardDeletedYn
) { }

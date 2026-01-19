package kr.or.kosa.backend.admin.dto.request;

public record DeleteBoardRequestDto(
    long boardId,
    String boardType
) {
}

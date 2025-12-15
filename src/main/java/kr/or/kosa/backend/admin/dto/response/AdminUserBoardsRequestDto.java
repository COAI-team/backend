package kr.or.kosa.backend.admin.dto.response;

public record AdminUserBoardsRequestDto(
    long userId,
    String boardTitle,
    String userNickNaem
)
{}

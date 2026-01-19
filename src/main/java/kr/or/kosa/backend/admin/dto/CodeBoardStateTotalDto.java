package kr.or.kosa.backend.admin.dto;

public record CodeBoardStateTotalDto(
    String name,
    int totalCount
) {
    public CodeBoardStateTotalDto {
        name =  (name == null) ? "TOTAL" : name;
    }
}

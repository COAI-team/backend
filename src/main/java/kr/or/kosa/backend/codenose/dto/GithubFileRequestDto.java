package kr.or.kosa.backend.codenose.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubFileRequestDto {
    private String owner;        // 레포지토리 소유자
    private String repo;         // 레포지토리 이름
    private String path;         // 파일 경로
}


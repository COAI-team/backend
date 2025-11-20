package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubFileResponseDto {
    private String name;         // 파일명
    private String path;         // 파일 경로
    private String content;      // 파일 내용 (Base64 디코딩됨)
    private String encoding;     // 인코딩 방식
    private int size;           // 파일 크기
}

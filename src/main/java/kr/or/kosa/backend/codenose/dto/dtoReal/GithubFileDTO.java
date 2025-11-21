package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub 파일 DTO
 * GitHub API 요청/응답 데이터 전송용 (데이터베이스 테이블 매핑 없음)
 *
 * GitHub API를 통해 파일 내용을 요청하고 응답받을 때 사용하는 DTO
 * - Request: owner, repo, path로 파일 요청
 * - Response: 파일 메타데이터 및 Base64 디코딩된 내용 반환
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubFileDTO {
    // === Request 필드 ===
    private String owner;                   // 레포지토리 소유자
    private String repo;                    // 레포지토리 이름

    // === Response 필드 ===
    private String name;                    // 파일명
    private String path;                    // 파일 경로
    private String content;                 // 파일 내용 (Base64 디코딩됨)
    private String encoding;                // 인코딩 방식 (예: base64)
    private int size;                       // 파일 크기 (bytes)

    /**
     * Request용 생성자
     */
    public GithubFileDTO(String owner, String repo, String path) {
        this.owner = owner;
        this.repo = repo;
        this.path = path;
    }

    /**
     * Response용 생성자
     */
    public GithubFileDTO(String name, String path, String content, String encoding, int size) {
        this.name = name;
        this.path = path;
        this.content = content;
        this.encoding = encoding;
        this.size = size;
    }
}

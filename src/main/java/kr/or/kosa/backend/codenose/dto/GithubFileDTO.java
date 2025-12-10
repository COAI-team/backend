package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * GitHub 파일 DTO (GithubFileDTO)
 * 
 * 역할:
 * 1. GitHub API로부터 파일 정보를 받아올 때 사용됩니다.
 * 2. `GITHUB_FILES` 테이블과 매핑되어 파일 정보를 DB에 저장하거나 조회할 때 사용됩니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubFileDTO {
    // === DB 컬럼 매핑 ===
    private String fileId; // FILE_ID (PK) - UUID
    private Long userId; // USER_ID
    private String repositoryUrl; // REPOSITORY_URL
    private String owner; // 레포지토리 소유자
    private String repo; // 레포지토리 이름
    private String filePath; // 파일 경로
    private String fileName; // 파일 이름
    private String fileContent; // 파일 내용 (텍스트)
    private Integer fileSize; // 파일 크기 (bytes)
    private String encoding; // 인코딩 방식 (예: base64, utf-8)
    private LocalDateTime createdAt; // 생성 일시
    private LocalDateTime updatedAt; // 수정 일시

    // === GitHub API Response용 편의 메서드 (별칭 Getter/Setter) ===
    // GitHub API는 'name', 'path', 'size', 'content' 등의 필드명을 사용하므로 이에 대응합니다.

    public String getName() {
        return fileName;
    }

    public void setName(String name) {
        this.fileName = name;
    }

    public String getPath() {
        return filePath;
    }

    public void setPath(String path) {
        this.filePath = path;
    }

    public String getContent() {
        return fileContent;
    }

    public void setContent(String content) {
        this.fileContent = content;
    }

    public int getSize() {
        return fileSize != null ? fileSize : 0;
    }

    public void setSize(int size) {
        this.fileSize = size;
    }

    /**
     * GitHub API Response 매핑용 생성자
     */
    public GithubFileDTO(String name, String path, String content, String encoding, int size) {
        this.fileName = name;
        this.filePath = path;
        this.fileContent = content;
        this.encoding = encoding;
        this.fileSize = size;
    }
}
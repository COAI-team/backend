package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 파일 저장 요청 DTO
 * GitHub 파일 내용을 DB에 저장하기 위한 요청 데이터
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileSaveRequestDTO {
    private String repositoryUrl;       // GitHub 레포지토리 URL
    private String owner;               // 레포지토리 소유자
    private String repo;                // 레포지토리 이름
    private String filePath;            // 파일 경로
    private Long userId;                // 사용자 ID (인증 컨텍스트에서 가져옴)
}

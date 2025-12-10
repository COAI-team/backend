package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 파일 저장 요청 DTO (FileSaveRequestDTO)
 * 
 * 역할:
 * GitHub API를 통해 조회한 파일 내용을 데이터베이스에 저장하라는 요청을 전달합니다.
 * 'AnalysisService'가 분석 전에 파일을 DB에 적재하는 단계에서 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileSaveRequestDTO {
    private String repositoryUrl; // GitHub 레포지토리 URL
    private String owner; // 레포지토리 소유자
    private String repo; // 레포지토리 이름
    private String filePath; // 파일 경로 (예: src/main/java/Main.java)
    private Long userId; // 요청을 수행하는 사용자 ID (인증 컨텍스트 기반)
}

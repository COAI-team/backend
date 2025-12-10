package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 트리 엔트리 DTO (GithubTreeEntryDTO)
 * 
 * 역할:
 * GitHub API의 Tree (파일/디렉토리 구조) 조회 응답 데이터를 담습니다.
 * 레포지토리 탐색기에서 파일 목록을 보여줄 때 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubTreeEntryDTO {
    private String path; // 파일 또는 디렉토리의 전체 경로
    private String type; // 항목 유형: "blob" (파일) 또는 "tree" (디렉토리)
}

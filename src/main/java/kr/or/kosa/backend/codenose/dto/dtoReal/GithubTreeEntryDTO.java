package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 트리 엔트리 DTO
 * GitHub API 응답 데이터 전송용 (데이터베이스 테이블 매핑 없음)
 *
 * GitHub API를 통해 가져온 파일 트리 정보를 담는 DTO
 * - 레포지토리의 파일/디렉토리 구조를 표현
 * - type: "blob" (파일) 또는 "tree" (디렉토리)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubTreeEntryDTO {
    private String path;                    // 파일/디렉토리 경로
    private String type;                    // 타입: "blob" (파일) 또는 "tree" (디렉토리)
}

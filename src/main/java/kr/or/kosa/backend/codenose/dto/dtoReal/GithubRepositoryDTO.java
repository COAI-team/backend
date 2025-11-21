package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 레포지토리 DTO
 * GitHub API 응답 데이터 전송용 (데이터베이스 테이블 매핑 없음)
 *
 * GitHub API를 통해 가져온 레포지토리 정보를 담는 DTO
 * - 사용자의 레포지토리 목록 조회 시 사용
 * - 필요 시 USER 테이블과 연관지어 저장 가능 (향후 확장)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepositoryDTO {
    private String name;                    // 레포지토리 이름
    private String fullName;                // 전체 이름 (owner/repo)
    private String url;                     // 레포지토리 URL
    private String owner;                   // 소유자 이름
}

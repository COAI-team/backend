package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 레포지토리 DTO (GithubRepositoryDTO)
 * 
 * 역할:
 * GitHub API에서 사용자의 레포지토리 목록을 조회했을 때의 응답 데이터를 담습니다.
 * 사용자가 분석할 프로젝트를 선택하는 화면에서 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepositoryDTO {
    private String name; // 레포지토리 이름 (예: my-project)
    private String fullName; // 소유자 포함 전체 이름 (예: user/my-project)
    private String url; // 레포지토리 HTTPS URL
    private String owner; // 소유자 아이디 (login ID)
}

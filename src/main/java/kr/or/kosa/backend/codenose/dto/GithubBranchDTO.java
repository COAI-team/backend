package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 브랜치 DTO (GithubBranchDTO)
 * 
 * 역할:
 * GitHub API에서 특정 레포지토리의 브랜치 목록을 조회했을 때의 응답 데이터를 담습니다.
 * 이 객체는 DB에 저장되지 않으며, 사용자에게 브랜치 선택 옵션을 제공할 때 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubBranchDTO {
    private String name; // 브랜치 이름 (예: main, develop, feature/login)
}

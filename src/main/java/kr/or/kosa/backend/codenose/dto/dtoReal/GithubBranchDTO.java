package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 브랜치 DTO
 * GitHub API 응답 데이터 전송용 (데이터베이스 테이블 매핑 없음)
 *
 * GitHub API를 통해 가져온 브랜치 정보를 담는 DTO
 * - 특정 레포지토리의 브랜치 목록 조회 시 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubBranchDTO {
    private String name;                    // 브랜치 이름 (예: main, develop)
}

package kr.or.kosa.backend.auth.github.mapper;

import kr.or.kosa.backend.auth.github.dto.UserGithubSettingsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 GitHub 설정 관련 MyBatis 매퍼
 *
 * @since 2025-12-13
 */
@Mapper
public interface UserGithubSettingsMapper {

    /**
     * 사용자 ID로 GitHub 설정 조회
     * @param userId 사용자 ID
     * @return GitHub 설정 정보 (없으면 null)
     */
    UserGithubSettingsDto selectByUserId(@Param("userId") Long userId);

    /**
     * GitHub 설정 저장 (INSERT)
     * @param settings 설정 정보
     * @return 영향받은 행 수
     */
    int insert(UserGithubSettingsDto settings);

    /**
     * GitHub 설정 업데이트
     * @param settings 설정 정보
     * @return 영향받은 행 수
     */
    int update(UserGithubSettingsDto settings);

    /**
     * GitHub 설정 삭제
     * @param userId 사용자 ID
     * @return 영향받은 행 수
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 자동 커밋 설정만 업데이트
     * @param userId 사용자 ID
     * @param autoCommitEnabled 자동 커밋 활성화 여부
     * @return 영향받은 행 수
     */
    int updateAutoCommitEnabled(
            @Param("userId") Long userId,
            @Param("autoCommitEnabled") Boolean autoCommitEnabled
    );

    /**
     * 저장소 정보만 업데이트
     * @param userId 사용자 ID
     * @param githubRepoName 저장소명
     * @param githubRepoUrl 저장소 URL
     * @return 영향받은 행 수
     */
    int updateRepository(
            @Param("userId") Long userId,
            @Param("githubRepoName") String githubRepoName,
            @Param("githubRepoUrl") String githubRepoUrl
    );
}

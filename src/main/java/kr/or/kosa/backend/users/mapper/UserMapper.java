package kr.or.kosa.backend.users.mapper;

import kr.or.kosa.backend.users.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

        /**
         * 사용자 ID로 단일 사용자 조회
         */
        Users findById(Long userId);

        /**
         * 이메일로 사용자 조회 (회원가입/로그인 중복 체크)
         */
        Users findByEmail(@Param("userEmail") String userEmail);

        /**
         * 닉네임으로 사용자 조회 (중복 체크)
         */
        Users findByNickname(@Param("userNickname") String userNickname);

        /**
         * 신규 사용자 등록
         */
        int insertUser(Users users);

        /**
         * 사용자 프로필 이미지 업데이트
         */
        int updateUserImage(
                @Param("userId") Long userId,
                @Param("userImage") String userImage);

        /**
         * 사용자 비밀번호 업데이트 (재설정)
         */
        int updatePassword(
                @Param("userId") Long userId,
                @Param("userPw") String userPw);

        /**
         * 모든 필드 업데이트 (이름, 닉네임, 이미지, GitHub 정보 등)
         */
        int updateUser(Users users);

        /**
         * 탈퇴 예약 설정 (90일 후 삭제)
         */
        int scheduleDelete(
                @Param("userId") Long userId,
                @Param("userDeletedat") LocalDateTime userDeletedat);

        /**
         * 탈퇴 예약 취소 및 복구
         */
        int restoreUser(Long userId);

        /**
         * 소셜 프로바이더로 사용자 조회 (GitHub 연동)
         */
        Users findBySocialProvider(
                @Param("provider") String provider,
                @Param("providerId") String providerId);

        /**
         * 소셜 계정 연동 정보 저장
         */
        int insertSocialAccount(
                @Param("userId") Long userId,
                @Param("provider") String provider,
                @Param("providerId") String providerId,
                @Param("userEmail") String userEmail);

        /**
         * 다중 사용자 ID로 사용자 목록 조회
         */
        List<Users> selectUsersByIds(@Param("userIds") List<Long> userIds);

        /**
         * 삭제 예정자 조회 (실제 삭제 배치 작업용)
         */
        List<Users> findUsersToDelete(@Param("now") LocalDateTime now);

        /**
         * 소프트 삭제
         */
        int softDeleteUser(@Param("userId") Long userId);

        /**
         * 사용자 정보 익명화 (실제 삭제 전)
         */
        int anonymizeUser(
                @Param("userId") Long userId,
                @Param("userEmail") String userEmail,
                @Param("userName") String userName);

        /**
         * 소셜 계정 연동 해제
         */
        int deleteSocialAccount(Long userId, String provider);

        /**
         * 소셜 계정 연동 여부 확인
         */
        Integer countSocialAccount(Long userId, String provider);

        /**
         * GitHub 연동 사용자 정보 조회
         */
        Map<String, Object> getGithubUserInfo(@Param("userId") Long userId);

        /**
         * 사용자별 소셜 프로바이더 조회
         */
        String findSocialProviderByUserId(@Param("userId") Long userId);

        /**
         * MCP 토큰 업데이트
         */
        int updateMcpToken(@Param("userId") Long userId, @Param("mcpToken") String mcpToken);

        /**
         * MCP 토큰으로 사용자 조회
         */
        Users findByMcpToken(@Param("mcpToken") String mcpToken);

        List<Users> findByIds(@Param("userIds") List<Long> userIds);
        List<Users> findNicknamesByIds(@Param("userIds") List<Long> userIds);
}


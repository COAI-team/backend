package kr.or.kosa.backend.users.mapper;

import kr.or.kosa.backend.users.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    Users findById(Long userId);

    Users findByEmail(@Param("userEmail") String userEmail);

    Users findByNickname(@Param("userNickname") String userNickname);

    int insertUser(Users users);

    int updateUserImage(
            @Param("userId") Long userId,
            @Param("userImage") String userImage
    );

    int updatePassword(
            @Param("userId") Long userId,
            @Param("userPw") String userPw
    );

    int updateUserInfo(
            @Param("userId") Long userId,
            @Param("userName") String userName,
            @Param("userNickname") String userNickname
    );

    int scheduleDelete(
            @Param("userId") Long userId,
            @Param("userDeletedat") LocalDateTime userDeletedat
    );

    int restoreUser(Long userId);

    Users findBySocialProvider(
            @Param("provider") String provider,
            @Param("providerId") String providerId
    );

    int insertSocialAccount(
            @Param("userId") Long userId,
            @Param("provider") String provider,
            @Param("providerId") String providerId,
            @Param("userEmail") String userEmail
    );

    List<Users> selectUsersByIds(@Param("userIds") List<Long> userIds);

    List<Users> findUsersToDelete(@Param("now") LocalDateTime now);

    int softDeleteUser(@Param("userId") Long userId);

    int anonymizeUser(
            @Param("userId") Long userId,
            @Param("userEmail") String userEmail,
            @Param("userName") String userName
    );

    int deleteSocialAccount(Long userId, String provider);

    Integer countSocialAccount(Long userId, String provider);

    Map<String, Object> getGithubUserInfo(@Param("userId") Long userId);

    String findSocialProviderByUserId(@Param("userId") Long userId);
}
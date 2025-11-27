package kr.or.kosa.backend.user.mapper;

import kr.or.kosa.backend.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    User findByEmail(@Param("email") String email);

    User findByNickname(@Param("nickname") String nickname);

    User findById(@Param("id") Integer id);

    int insertUser(User user);

    // 프로필 이미지 수정
    int updateUserImage(@Param("id") int id, @Param("image") String image);

    // 비밀번호 수정
    int updatePassword(@Param("id") int id, @Param("password") String password);

    // 이름 + 닉네임 수정
    int updateUserInfo(@Param("id") int id, @Param("name") String name, @Param("nickname") String nickname);

    // 이메일 수정
    int updateUserEmail(@Param("id") Integer id, @Param("email") String email);

    // 90일 뒤 자동 탈퇴 기능 추가
    // 1) 탈퇴 예약
    int scheduleDelete(@Param("id") int userId, @Param("deletedAt") LocalDateTime deletedAt);

    // 2) 복구
    int restoreUser(@Param("id") int userId);

    // 3) 90일 경과한 유저 목록 조회
    List<User> findUsersToDelete(@Param("now") LocalDateTime now);

    // 4) Soft Delete 처리
    int softDeleteUser(@Param("id") int userId);

    // 5) 개인정보 익명화
    int anonymizeUser(@Param("id") int userId,
                      @Param("email") String email,
                      @Param("name") String name);

}
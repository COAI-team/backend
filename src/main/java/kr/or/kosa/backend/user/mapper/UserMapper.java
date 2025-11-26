package kr.or.kosa.backend.user.mapper;

import kr.or.kosa.backend.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByEmail(@Param("email") String email);

    User findByNickname(@Param("nickname") String nickname);

    User findById(@Param("id") Integer id);

    int insertUser(User user);

    int insertGithubUser(User user);

    // 프로필 이미지 수정
    int updateUserImage(
            @Param("id") int id,
            @Param("image") String image
    );

    // 비밀번호 수정
    int updatePassword(
            @Param("id") int id,
            @Param("password") String password
    );

    // 이름 + 닉네임 수정
    int updateUserInfo(
            @Param("id") int id,
            @Param("name") String name,
            @Param("nickname") String nickname
    );

    // 이메일 수정
    int updateUserEmail(
            @Param("id") Integer id,
            @Param("email") String email
    );

    // refresh token 수정 (DB 저장형일 경우)
    int updateRefreshToken(
            @Param("id") Integer id,
            @Param("token") String token
    );

    // 유저 삭제
    int deleteUser(@Param("id") Integer id);
}
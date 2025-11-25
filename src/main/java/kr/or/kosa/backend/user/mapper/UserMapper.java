package kr.or.kosa.backend.user.mapper;

import kr.or.kosa.backend.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByEmail(String email);

    User findByNickname(String nickname);

    User findById(Integer id);

    void insertUser(User user);

    void updateUserImage(
            @Param("id") int id,
            @Param("image") String image
    );

    void updatePassword(
            @Param("id") int id,
            @Param("password") String password
    );
}
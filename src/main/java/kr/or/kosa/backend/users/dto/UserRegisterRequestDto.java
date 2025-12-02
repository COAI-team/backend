package kr.or.kosa.backend.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserRegisterRequestDto {

    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            message = "유효한 이메일 주소가 아닙니다. 예: example@gmail.com"
    )
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    private String userEmail;

    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-{}\\[\\]|:;\"'<>,.?/]).{8,20}$",
            message = "비밀번호는 8~20자이며, 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다."
    )
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String userPw;

    @Pattern(
            regexp = "^[\\p{L}][\\p{L} '\\-]{1,49}$",
            message = "숫자와 기타 특수문자는 사용할 수 없습니다."
    )
    @NotBlank(message = "이름은 필수 입력값입니다.")
    private String userName;

    @Pattern(
            regexp = "^[A-Za-z0-9_-]{3,20}$",
            message = "닉네임은 영문 대소문자, 숫자, '_', '-'만 사용할 수 있으며 3~20자여야 합니다."
    )
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    private String userNickname;
}
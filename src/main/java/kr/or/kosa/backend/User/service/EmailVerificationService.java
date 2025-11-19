package kr.or.kosa.backend.user.service;

public interface EmailVerificationService {

    /** 인증 코드 생성 및 이메일 전송 */
    long sendVerificationEmail(String email);

    /** 인증 코드 확인 */
    boolean verifyCode(String email, String code);

    /** 인증 체크 */
    boolean isVerified(String email);

    void send(String to, String subject, String text);
}

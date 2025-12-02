package kr.or.kosa.backend.users.service;

public interface EmailVerificationService {

    /** 인증 코드 생성 및 이메일 전송 */
    long sendVerificationEmail(String email);

    /** 인증 코드 확인 및 인증 완료 처리(verified=true) */
    boolean verifyCodeAndUpdate(String email, String code);

    /** 이메일 인증 여부 확인 */
    boolean isVerified(String email);

    /** 이메일 전송 */
    boolean send(String to, String subject, String text);
}
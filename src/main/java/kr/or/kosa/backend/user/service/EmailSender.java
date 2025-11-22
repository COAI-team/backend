package kr.or.kosa.backend.user.service;

public interface EmailSender {
    void sendEmail(String to, String subject, String text);
}
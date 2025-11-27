package kr.or.kosa.backend.user.service;

public interface EmailSender {
    boolean sendEmail(String to, String subject, String text);
}
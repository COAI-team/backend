package kr.or.kosa.backend.users.service;

public interface EmailSender {
    boolean sendEmail(String to, String subject, String text);
}
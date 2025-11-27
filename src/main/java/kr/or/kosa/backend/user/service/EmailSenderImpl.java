package kr.or.kosa.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderImpl implements EmailSender {

    private final JavaMailSender mailSender;

    @Override
    public boolean sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);

            return true; // 성공
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            return false; // 실패
        }
    }
}
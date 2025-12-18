package kr.or.kosa.backend.users.service;

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
    /**
     * Reusable base message to avoid rebuilding defaults on every send.
     */
    private final SimpleMailMessage baseMessage = new SimpleMailMessage();

    @Override
    public boolean sendEmail(String to, String subject, String text) {
        if (to == null || to.isBlank() || subject == null || text == null) {
            log.warn("Skip email send due to invalid parameters: to={}, subject null? {}, text null? {}",
                    to, subject == null, text == null);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage(baseMessage);
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

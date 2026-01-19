package kr.or.kosa.backend.tag.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TagValidator implements ConstraintValidator<ValidTag, String> {

    @Override
    public void initialize(ValidTag constraintAnnotation) {
    }

    @Override
    public boolean isValid(String tag, ConstraintValidatorContext context) {
        if (tag == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("태그는 null일 수 없습니다.")
                    .addConstraintViolation();
            return false;
        }

        String trimmed = tag.trim();

        // 빈 문자열
        if (trimmed.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("태그는 빈 문자열일 수 없습니다.")
                    .addConstraintViolation();
            return false;
        }

        // 길이 체크
        if (trimmed.length() < 2) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("태그는 2자 이상이어야 합니다.")
                    .addConstraintViolation();
            return false;
        }

        if (trimmed.length() > 20) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("태그는 20자를 초과할 수 없습니다.")
                    .addConstraintViolation();
            return false;
        }

        // 특수문자 체크 (선택사항)
        if (containsInvalidCharacters(trimmed)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("태그에 사용할 수 없는 특수문자가 포함되어 있습니다.")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }

    private boolean containsInvalidCharacters(String tag) {
        // 허용: 한글, 영문, 숫자, 하이픈, 언더스코어
        // 불허용: 특수문자 (!, @, #, $, %, ^, & 등)
        return tag.matches(".*[!@#$%^&*()+=\\[\\]{};':\"\\\\|,.<>/?].*");
    }
}
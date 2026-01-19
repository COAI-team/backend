package kr.or.kosa.backend.tag.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TagValidator.class)
@Documented
public @interface ValidTag {

    String message() default "유효하지 않은 태그입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
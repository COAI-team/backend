package kr.or.kosa.backend.admin.dto;

import java.time.LocalDateTime;

public record BoardItem (
    long id,
    String title,
    String userNickName,
    LocalDateTime createTime,
    String check
) {}

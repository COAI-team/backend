package kr.or.kosa.backend.like.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Like {
    private Long likeId;
    private Long userId;
    private ReferenceType referenceType;
    private Long referenceId;
    private LocalDateTime createdAt;
}

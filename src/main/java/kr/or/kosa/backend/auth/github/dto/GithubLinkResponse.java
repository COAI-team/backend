package kr.or.kosa.backend.auth.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GithubLinkResponse {

    private final boolean success;
    private final String message;
}
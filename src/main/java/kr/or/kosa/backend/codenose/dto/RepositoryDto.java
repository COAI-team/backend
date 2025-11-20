package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryDto {
    private String name;
    private String fullName;
    private String url;
    private String owner;
}

package kr.or.kosa.backend.toobar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreviewResponse {
    private String title;
    private String description;
    private String image;
    private String site;
    private String url;
}

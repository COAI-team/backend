package kr.or.kosa.backend.codenose.service.pipeline;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PipelineContext {
    private String originalCode;
    private String userContext;
    private String styleRules;
    private String optimizedLogic;
    private String finalResult;
}

package kr.or.kosa.backend.admin.dto.response;

import kr.or.kosa.backend.admin.dto.CodeBoardAnalysisDetailDto;

public record AdminCodeBoardDetailResponseDto(
    CodeBoardAnalysisDetailDto CodeBoardDetail,
    String gitCode
) { }

package kr.or.kosa.backend.admin.dto;

public record CodeBoardAnalysisDetailDto(
    long codeboardId,          // c.CODEBOARD_ID
    long userId,               // c.USER_ID
    String analysisId,         // c.ANALYSIS_ID
    String codeboardTitle,     // c.CODEBOARD_TITLE
    String codeboardBlocks,    // c.CODEBOARD_BLOCKS (본문 블록)
    String codeboardDeletedYn, // c.CODEBOARD_DELETED_YN
    String repositoryUrl,      // cah.REPOSITORY_URL
    String filePath,           // cah.FILE_PATH
    String analysisType,       // cah.ANALYSIS_TYPE
    int toneLevel,         // cah.TONE_LEVEL
    String customRequirements, // cah.CUSTOM_REQUIREMENTS
    String analysisResults,    // cah.ANALYSIS_RESULTS
    int aiScore            // cah.AI_SCORE
) {

    public String getOwner(){
        String[] parts = repositoryUrl.split("/");
        return parts[parts.length - 2];
    }

    public String getRepo(){
        String[] parts = repositoryUrl.split("/");
        return parts[parts.length - 1];
    }
}




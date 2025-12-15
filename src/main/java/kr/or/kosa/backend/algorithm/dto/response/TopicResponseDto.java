package kr.or.kosa.backend.algorithm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 알고리즘 토픽 카테고리별 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class TopicResponseDto {

    /** 카테고리명 (예: 자료구조, 탐색, 최적화, 구현) */
    private String category;

    /** 해당 카테고리의 토픽 목록 */
    private List<TopicItem> topics;

    /**
     * 개별 토픽 정보
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class TopicItem {
        /** enum 이름 (예: DFS_BFS) */
        private String value;

        /** 화면 표시명 (예: DFS/BFS) */
        private String displayName;
    }
}

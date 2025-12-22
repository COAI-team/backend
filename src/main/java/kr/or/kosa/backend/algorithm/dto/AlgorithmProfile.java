package kr.or.kosa.backend.algorithm.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Phase 1: 알고리즘 프로필 정의
 *
 * 각 알고리즘 주제에 대한 전체 프로필을 정의합니다:
 * - 표시 이름 (displayName)
 * - 난이도별 스펙 (difficultySpecs)
 * - 프롬프트 추가사항 (promptAdditions)
 */
@Getter
@Setter
public class AlgorithmProfile {

    private String displayName;
    private Map<String, DifficultySpec> difficultySpecs;
    private String promptAdditions;

    public AlgorithmProfile() {
    }

    /**
     * 특정 난이도의 스펙 조회
     *
     * @param difficulty 난이도 (BRONZE, SILVER, GOLD, PLATINUM)
     * @return 난이도 스펙 (없으면 기본값 반환)
     */
    public DifficultySpec getSpec(String difficulty) {
        if (difficultySpecs == null || !difficultySpecs.containsKey(difficulty)) {
            return DifficultySpec.defaultSpec(difficulty);
        }
        return difficultySpecs.get(difficulty);
    }

    /**
     * 프롬프트 추가사항이 있는지 확인
     */
    public boolean hasPromptAdditions() {
        return promptAdditions != null && !promptAdditions.isBlank();
    }
}

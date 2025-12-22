package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgorithmProfile;
import kr.or.kosa.backend.algorithm.dto.DifficultySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 1: 알고리즘 프로필 레지스트리
 *
 * YAML 설정 파일(algorithm-profiles.yml)을 로드하여
 * 알고리즘 주제별, 난이도별 스펙을 제공합니다.
 *
 * 사용 예시:
 * - getDifficultySpec("DP", "GOLD") → DP 알고리즘 GOLD 난이도 스펙
 * - getMaxTokens("DP") → DP 알고리즘용 최대 토큰 수
 * - getPromptAdditions("DP") → DP 알고리즘용 프롬프트 추가사항
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmProfileRegistry {

    private final AlgorithmSynonymDictionary synonymDictionary;

    @Value("${algorithm.profiles.token-config.default:4096}")
    private int defaultMaxTokens;

    private Map<String, AlgorithmProfile> profiles = new HashMap<>();
    private Map<String, Integer> tokenOverrides = new HashMap<>();

    /**
     * 서비스 초기화 시 YAML 파일 로드
     */
    @PostConstruct
    public void init() {
        loadProfiles();
    }

    /**
     * YAML 프로필 파일 로드
     */
    @SuppressWarnings("unchecked")
    private void loadProfiles() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("config/algorithm-profiles.yml");

            if (inputStream == null) {
                log.warn("algorithm-profiles.yml 파일을 찾을 수 없습니다. 기본값을 사용합니다.");
                return;
            }

            Map<String, Object> config = yaml.load(inputStream);

            // profiles 섹션 로드
            Map<String, Object> profilesSection = (Map<String, Object>) config.get("profiles");
            if (profilesSection != null) {
                for (Map.Entry<String, Object> entry : profilesSection.entrySet()) {
                    String profileKey = entry.getKey();
                    Map<String, Object> profileData = (Map<String, Object>) entry.getValue();
                    AlgorithmProfile profile = parseProfile(profileData);
                    profiles.put(profileKey, profile);
                }
            }

            // tokenConfig 섹션 로드
            Map<String, Object> tokenConfig = (Map<String, Object>) config.get("tokenConfig");
            if (tokenConfig != null) {
                Integer defaultToken = (Integer) tokenConfig.get("default");
                if (defaultToken != null) {
                    defaultMaxTokens = defaultToken;
                }

                Map<String, Integer> overrides = (Map<String, Integer>) tokenConfig.get("overrides");
                if (overrides != null) {
                    tokenOverrides.putAll(overrides);
                }
            }

            log.info("알고리즘 프로필 로드 완료 - {} 개 프로필, 기본 토큰: {}",
                    profiles.size(), defaultMaxTokens);

        } catch (Exception e) {
            log.error("알고리즘 프로필 로드 실패", e);
        }
    }

    /**
     * YAML 데이터를 AlgorithmProfile 객체로 파싱
     */
    @SuppressWarnings("unchecked")
    private AlgorithmProfile parseProfile(Map<String, Object> data) {
        AlgorithmProfile profile = new AlgorithmProfile();
        profile.setDisplayName((String) data.get("displayName"));
        profile.setPromptAdditions((String) data.get("promptAdditions"));

        Map<String, Object> difficultySpecsData = (Map<String, Object>) data.get("difficultySpecs");
        if (difficultySpecsData != null) {
            Map<String, DifficultySpec> difficultySpecs = new HashMap<>();
            for (Map.Entry<String, Object> entry : difficultySpecsData.entrySet()) {
                String difficulty = entry.getKey();
                Map<String, Object> specData = (Map<String, Object>) entry.getValue();
                DifficultySpec spec = parseSpec(specData);
                difficultySpecs.put(difficulty, spec);
            }
            profile.setDifficultySpecs(difficultySpecs);
        }

        return profile;
    }

    /**
     * YAML 데이터를 DifficultySpec 객체로 파싱
     */
    private DifficultySpec parseSpec(Map<String, Object> data) {
        DifficultySpec spec = new DifficultySpec();
        spec.setInputSize((String) data.get("inputSize"));
        spec.setTimeLimit(getIntValue(data, "timeLimit", 1000));
        spec.setMemoryLimit(getIntValue(data, "memoryLimit", 256));
        spec.setTimeComplexity((String) data.get("timeComplexity"));
        spec.setDescription((String) data.get("description"));
        return spec;
    }

    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return defaultValue;
    }

    // ===== 공개 API =====

    /**
     * 알고리즘 주제를 정규화된 프로필 키로 변환
     *
     * @param topic 알고리즘 주제 (예: "동적 프로그래밍", "DP", "dynamic programming")
     * @return 정규화된 프로필 키 (예: "DP")
     */
    public String getProfileKey(String topic) {
        String normalized = synonymDictionary.normalize(topic).toUpperCase();

        // 동의어 사전 정규화 결과를 프로필 키로 매핑
        return switch (normalized) {
            case "DP", "KNAPSACK", "LIS" -> "DP";
            case "DFS", "BFS" -> "DFS_BFS";
            case "DIJKSTRA", "FLOYD_WARSHALL", "BELLMAN_FORD", "GRAPH" -> "SHORTEST_PATH";
            case "STACK", "QUEUE", "STACK_QUEUE" -> "STACK_QUEUE";
            case "PRIORITY_QUEUE" -> "PRIORITY_QUEUE";
            case "HASH" -> "HASH";
            case "TREE", "SEGMENT_TREE" -> "TREE";
            case "BINARY_SEARCH" -> "BINARY_SEARCH";
            case "GREEDY" -> "GREEDY";
            case "TWO_POINTER" -> "TWO_POINTER";
            case "STRING", "KMP" -> "STRING";
            case "SORTING" -> "SORTING";
            case "SIMULATION", "IMPLEMENTATION" -> "IMPLEMENTATION";
            case "RECURSION", "DIVIDE_CONQUER" -> "BACKTRACKING";
            default -> normalized;
        };
    }

    /**
     * 특정 주제/난이도의 스펙 조회
     *
     * @param topic      알고리즘 주제
     * @param difficulty 난이도 (BRONZE, SILVER, GOLD, PLATINUM)
     * @return 난이도 스펙 (프로필 없으면 기본값 반환)
     */
    public DifficultySpec getDifficultySpec(String topic, String difficulty) {
        String key = getProfileKey(topic);
        AlgorithmProfile profile = profiles.get(key);

        if (profile == null) {
            log.debug("프로필 없음: {} → 기본 스펙 반환", key);
            return DifficultySpec.defaultSpec(difficulty);
        }

        return profile.getSpec(difficulty);
    }

    /**
     * 특정 주제의 최대 토큰 수 조회
     *
     * @param topic 알고리즘 주제
     * @return 최대 토큰 수
     */
    public int getMaxTokens(String topic) {
        String key = getProfileKey(topic);
        return tokenOverrides.getOrDefault(key, defaultMaxTokens);
    }

    /**
     * 특정 주제의 프롬프트 추가사항 조회
     *
     * @param topic 알고리즘 주제
     * @return 프롬프트 추가사항 (없으면 null)
     */
    public String getPromptAdditions(String topic) {
        String key = getProfileKey(topic);
        AlgorithmProfile profile = profiles.get(key);

        if (profile != null && profile.hasPromptAdditions()) {
            return profile.getPromptAdditions();
        }
        return null;
    }

    /**
     * 특정 주제의 표시 이름 조회
     *
     * @param topic 알고리즘 주제
     * @return 표시 이름 (없으면 원본 반환)
     */
    public String getDisplayName(String topic) {
        String key = getProfileKey(topic);
        AlgorithmProfile profile = profiles.get(key);

        if (profile != null && profile.getDisplayName() != null) {
            return profile.getDisplayName();
        }
        return topic;
    }

    /**
     * 프로필 존재 여부 확인
     */
    public boolean hasProfile(String topic) {
        String key = getProfileKey(topic);
        return profiles.containsKey(key);
    }

    /**
     * 전체 프로필 수 조회 (테스트/모니터링용)
     */
    public int getProfileCount() {
        return profiles.size();
    }
}

package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.PoolProblemDto;
import kr.or.kosa.backend.algorithm.dto.PoolStatusDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemTopic;
import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.mapper.ProblemPoolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 문제 풀 서비스
 * <p>풀에서 문제 소비, 풀 채우기, 상태 조회 담당
 * <p>흐름: 풀에서 소비 → ALGO_PROBLEMS 저장 → 풀에서 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemPoolService {

    private final ProblemPoolMapper poolMapper;
    private final ProblemGenerationOrchestrator generationOrchestrator;
    private final AlgorithmProblemService problemService;
    private final ProblemVectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;

    @Value("${algorithm.pool.target-per-combination:5}")
    private int targetPerCombination;

    // ===== 문제 소비 (Draw) =====

    /**
     * 풀에서 문제 꺼내기 (사용자 요청 시 호출)
     * <p>1. 풀에서 조회 (SELECT FOR UPDATE)
     * <p>2. 있으면: JSON 파싱 → DB 저장 → 풀에서 삭제
     * <p>3. 없으면: 실시간 생성 (Fallback)
     *
     * @param difficulty       난이도
     * @param topic            알고리즘 주제 (displayName)
     * @param theme            스토리 테마
     * @param userId           사용자 ID (ALGO_CREATER)
     * @param progressCallback 진행률 콜백 (실시간 생성 시 사용)
     * @return 생성된 문제 응답
     */
    @Transactional
    public ProblemGenerationResponseDto drawProblem(
            String difficulty,
            String topic,
            String theme,
            Long userId,
            Consumer<ProblemGenerationOrchestrator.ProgressEvent> progressCallback) {

        log.info("풀에서 문제 꺼내기 시도 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);

        // 1. 풀에서 조회 (SELECT FOR UPDATE로 동시성 제어)
        PoolProblemDto poolProblem = poolMapper.findAndLockOne(difficulty, topic, theme);

        if (poolProblem != null) {
            // 2. 풀에 문제가 있으면: JSON 파싱 → DB 저장 → 풀에서 삭제
            log.info("풀에서 문제 발견 - poolId: {}", poolProblem.getAlgoPoolId());
            return consumeFromPool(poolProblem, userId);
        }

        // 3. 풀이 비어있으면: 실시간 생성 (Fallback)
        log.warn("풀이 비어있음 - 실시간 생성으로 전환 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);
        return generateRealtime(difficulty, topic, theme, userId, progressCallback);
    }

    /**
     * 풀에서 문제 소비 (JSON 파싱 → DB 저장 → 풀 삭제)
     */
    private ProblemGenerationResponseDto consumeFromPool(PoolProblemDto poolProblem, Long userId) {
        try {
            // 1. JSON 역직렬화
            ProblemGenerationResponseDto response = objectMapper.readValue(
                    poolProblem.getProblemContent(),
                    ProblemGenerationResponseDto.class
            );

            // 2. ALGO_PROBLEMS, ALGO_TESTCASES, PROBLEM_VALIDATION_LOGS에 저장
            Long problemId = problemService.saveGeneratedProblem(response, userId);
            response.setProblemId(problemId);

            // 3. 풀에서 삭제
            poolMapper.deleteById(poolProblem.getAlgoPoolId());

            log.info("풀에서 문제 소비 완료 - poolId: {} → problemId: {}", poolProblem.getAlgoPoolId(), problemId);
            return response;

        } catch (JsonProcessingException e) {
            log.error("풀 문제 JSON 파싱 실패 - poolId: {}", poolProblem.getAlgoPoolId(), e);
            throw new RuntimeException("풀 문제 파싱 실패", e);
        }
    }

    /**
     * 실시간 문제 생성 (Fallback)
     */
    private ProblemGenerationResponseDto generateRealtime(
            String difficulty,
            String topic,
            String theme,
            Long userId,
            Consumer<ProblemGenerationOrchestrator.ProgressEvent> progressCallback) {

        ProblemGenerationRequestDto request = ProblemGenerationRequestDto.builder()
                .difficulty(ProblemDifficulty.fromDbValue(difficulty))
                .topic(topic)
                .additionalRequirements("스토리 테마: " + theme)
                .build();

        // 기존 generateProblem 사용 (생성 + 저장 + 진행률 콜백)
        return generationOrchestrator.generateProblem(request, userId, progressCallback);
    }

    // ===== 데일리 미션용 문제 소비 =====

    /**
     * 데일리 미션용 - 난이도만으로 풀에서 문제 꺼내기
     * <p>Pool에서 해당 난이도의 랜덤 문제를 가져와 DB에 저장 후 problemId 반환
     * <p>Pool이 비어있으면 null 반환 (기존 ALGO_PROBLEM에서 fallback 필요)
     *
     * @param difficulty 난이도 (DB 값: BRONZE, SILVER, GOLD, PLATINUM)
     * @param userId     사용자 ID (ALGO_CREATER에 저장될 값, nullable - 시스템 생성 시)
     * @return 저장된 problemId, Pool이 비어있으면 null
     */
    @Transactional
    public Long drawProblemForDailyMission(String difficulty, Long userId) {
        log.info("데일리 미션용 풀에서 문제 꺼내기 - difficulty: {}", difficulty);

        // 1. Pool에서 해당 난이도의 문제 1개 조회 (SELECT FOR UPDATE)
        PoolProblemDto poolProblem = poolMapper.findAndLockOneByDifficulty(difficulty);

        if (poolProblem == null) {
            log.warn("데일리 미션용 풀이 비어있음 - difficulty: {}", difficulty);
            return null;  // 호출측에서 기존 ALGO_PROBLEM fallback 처리
        }

        // 2. JSON 파싱 → DB 저장 → 풀에서 삭제
        ProblemGenerationResponseDto response = consumeFromPool(poolProblem, userId);

        log.info("데일리 미션용 문제 소비 완료 - poolId: {} → problemId: {}",
                poolProblem.getAlgoPoolId(), response.getProblemId());

        return response.getProblemId();
    }

    // ===== 풀 채우기 (Generate for Pool) =====

    /**
     * 풀에 문제 추가 (스케줄러에서 호출)
     * <p>1. 문제 생성 (저장 없음)
     * <p>2. JSON 직렬화 → 풀에 저장
     * <p>3. Vector DB에 저장 (유사도 검사용)
     *
     * @param difficulty 난이도
     * @param topic      알고리즘 주제 (displayName)
     * @param theme      스토리 테마
     * @return 생성된 풀 문제 ID
     */
    @Transactional
    public Long generateForPool(String difficulty, String topic, String theme) {
        log.info("풀 채우기 시작 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);

        long startTime = System.currentTimeMillis();

        try {
            // 1. 문제 생성 (저장 없음)
            ProblemGenerationRequestDto request = ProblemGenerationRequestDto.builder()
                    .difficulty(ProblemDifficulty.fromDbValue(difficulty))
                    .topic(topic)
                    .additionalRequirements("스토리 테마: " + theme)
                    .build();

            ProblemGenerationResponseDto generated = generationOrchestrator.generateWithoutSaving(request);

            // 2. JSON 직렬화
            String contentJson = objectMapper.writeValueAsString(generated);

            // 3. 풀에 저장
            int generationTimeMs = (int) (System.currentTimeMillis() - startTime);
            PoolProblemDto poolProblem = PoolProblemDto.builder()
                    .difficulty(difficulty)
                    .topic(topic)
                    .theme(theme)
                    .problemContent(contentJson)
                    .generatedAt(LocalDateTime.now())
                    .generationTimeMs(generationTimeMs)
                    .build();

            poolMapper.insert(poolProblem);

            // 4. Vector DB에 저장 (유사도 검사용 - 풀 문제도 포함)
            storeToVectorDb(generated, poolProblem.getAlgoPoolId());

            log.info("풀 채우기 완료 - poolId: {}, 소요시간: {}ms", poolProblem.getAlgoPoolId(), generationTimeMs);
            return poolProblem.getAlgoPoolId();

        } catch (JsonProcessingException e) {
            log.error("풀 문제 JSON 직렬화 실패", e);
            throw new RuntimeException("풀 문제 직렬화 실패", e);
        }
    }

    /**
     * Vector DB에 저장 (유사도 검사용)
     * <p>풀 문제는 음수 ID로 구분 (예: poolId=5 → vectorId=-5)
     */
    private void storeToVectorDb(ProblemGenerationResponseDto problem, Long poolId) {
        try {
            var problemDto = problem.getProblem();
            List<String> tags = problemDto.getAlgoProblemTags() != null
                    ? Arrays.asList(problemDto.getAlgoProblemTags().replace("[", "").replace("]", "").replace("\"", "").split(","))
                    : List.of();

            String difficulty = problemDto.getAlgoProblemDifficulty() != null
                    ? problemDto.getAlgoProblemDifficulty().name()
                    : "UNKNOWN";

            // 풀 문제는 음수 ID로 저장하여 구분
            vectorStoreService.storeGeneratedProblem(
                    -poolId,  // 음수 ID로 풀 문제 구분
                    problemDto.getAlgoProblemTitle(),
                    problemDto.getAlgoProblemDescription(),
                    difficulty,
                    tags
            );
            log.debug("Vector DB 저장 완료 (풀) - poolId: {}", poolId);

        } catch (Exception e) {
            log.warn("Vector DB 저장 실패 (무시하고 계속 진행) - poolId: {}", poolId, e);
        }
    }

    // ===== 풀 상태 조회 =====

    /**
     * 풀 전체 상태 조회
     *
     * @param themes 현재 활성화된 테마 목록
     * @return 풀 상태 DTO
     */
    public PoolStatusDto getPoolStatus(List<String> themes) {
        int totalCount = poolMapper.countAll();

        // 목표 개수 계산: 4 난이도 × 15 주제 × 테마 수 × 조합당 목표
        int combinationCount = ProblemDifficulty.values().length
                * ProblemTopic.values().length
                * themes.size();
        int targetTotal = combinationCount * targetPerCombination;

        double fillRate = targetTotal > 0 ? (double) totalCount / targetTotal * 100 : 0;

        // 난이도별 개수
        Map<String, Integer> byDifficulty = new HashMap<>();
        for (ProblemDifficulty difficulty : ProblemDifficulty.values()) {
            int count = poolMapper.countByDifficulty(difficulty.getDbValue());
            byDifficulty.put(difficulty.getDbValue(), count);
        }

        return PoolStatusDto.builder()
                .totalCount(totalCount)
                .targetTotal(targetTotal)
                .fillRate(Math.round(fillRate * 100.0) / 100.0)
                .byDifficulty(byDifficulty)
                .build();
    }

    /**
     * 특정 조합의 현재 문제 개수 조회
     */
    public int getCountByCombination(String difficulty, String topic, String theme) {
        return poolMapper.countByCombination(difficulty, topic, theme);
    }

    /**
     * 부족한 조합 목록 조회 (스케줄러에서 사용)
     *
     * @param themes 현재 활성화된 테마 목록
     * @return 부족한 조합 목록 (difficulty, topic, theme, currentCount, deficit)
     */
    public List<Map<String, Object>> getDeficientCombinations(List<String> themes) {
        // DB에서 현재 개수가 목표보다 적은 조합 조회
        List<Map<String, Object>> existingDeficient = poolMapper.findDeficientCombinations(targetPerCombination);

        // 아예 없는 조합도 추가 (DB에 0개인 조합은 조회 안됨)
        Map<String, Map<String, Object>> deficientMap = new HashMap<>();

        // 모든 가능한 조합 초기화
        for (ProblemDifficulty difficulty : ProblemDifficulty.values()) {
            for (ProblemTopic topic : ProblemTopic.values()) {
                for (String theme : themes) {
                    String key = difficulty.getDbValue() + "|" + topic.getDisplayName() + "|" + theme;
                    Map<String, Object> combination = new HashMap<>();
                    combination.put("difficulty", difficulty.getDbValue());
                    combination.put("topic", topic.getDisplayName());
                    combination.put("theme", theme);
                    combination.put("currentCount", 0);
                    combination.put("deficit", targetPerCombination);
                    deficientMap.put(key, combination);
                }
            }
        }

        // DB에서 조회한 결과 반영
        for (Map<String, Object> existing : existingDeficient) {
            String key = existing.get("difficulty") + "|" + existing.get("topic") + "|" + existing.get("theme");
            if (deficientMap.containsKey(key)) {
                int currentCount = ((Number) existing.get("currentCount")).intValue();
                deficientMap.get(key).put("currentCount", currentCount);
                deficientMap.get(key).put("deficit", targetPerCombination - currentCount);
            }
        }

        // 부족한 것만 필터링 (deficit > 0)
        return deficientMap.values().stream()
                .filter(m -> ((Number) m.get("deficit")).intValue() > 0)
                .toList();
    }
}

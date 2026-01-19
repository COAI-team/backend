package kr.or.kosa.backend.commons.redis;

import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;

    private static final int TOP_N = 5;
    private static final Duration RANK_TTL = Duration.ofDays(2);

    /* =====================================================
     * 🔑 Key 생성 메서드
     * ===================================================== */

    /** 난이도별 랭킹 키 */
    private String buildDifficultyRankKey(LocalDate date, String difficulty) {
        return String.format(
            "algo:rank:%s:%s",
            date,
            difficulty.trim().toUpperCase()
        );
    }

    /** ⭐ 날짜별 전체 랭킹 Master 키 */
    private String buildDailyRankKey(LocalDate date) {
        return String.format("algo:rank:%s", date);
    }

    /* =====================================================
     * 🏆 랭킹 저장
     * ===================================================== */

    /**
     * 랭킹 저장
     * - 난이도별 ZSET
     * - 날짜별 전체 ZSET (Master)
     */
    public void setAlgoRank(long userId, String problemDifficulty, double finalScore) {
        LocalDate today = LocalDate.now();

        String difficultyKey = buildDifficultyRankKey(today, problemDifficulty);
        String dailyKey = buildDailyRankKey(today);

        // 난이도별 랭킹
        redisTemplate.opsForZSet()
            .add(difficultyKey, String.valueOf(userId), finalScore);
        redisTemplate.expire(difficultyKey, RANK_TTL);

        // ⭐ 전체 랭킹 (Master)
        redisTemplate.opsForZSet()
            .add(dailyKey, String.valueOf(userId), finalScore);
        redisTemplate.expire(dailyKey, RANK_TTL);

        log.info(
            "✅ REDIS RANK ADD | difficultyKey={} | dailyKey={} | userId={} | score={}",
            difficultyKey, dailyKey, userId, finalScore
        );
    }

    /* =====================================================
     * 📊 랭킹 조회 (공통)
     * ===================================================== */

    private List<AlgoRankDto> buildRankResult(
        Set<ZSetOperations.TypedTuple<Object>> tuples
    ) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // userId 추출
        List<Long> userIds = tuples.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(Long::parseLong)
            .toList();

        if (userIds.isEmpty()) {
            return List.of();
        }

        // DB에서 닉네임 조회
        Map<Long, String> nicknameMap =
            userMapper.findNicknamesByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                    Users::getUserId,
                    Users::getUserNickname,
                    (a, b) -> a
                ));

        // rank 포함 결과 생성
        int rank = 0;
        List<AlgoRankDto> result = new ArrayList<>();

        for (ZSetOperations.TypedTuple<Object> t : tuples) {
            if (t.getValue() == null) continue;

            rank++;
            long userId = Long.parseLong(t.getValue().toString());
            double score = t.getScore() == null ? 0.0 : t.getScore();

            result.add(new AlgoRankDto(
                rank,
                userId,
                nicknameMap.getOrDefault(userId, "Unknown"),
                score
            ));
        }

        return result;
    }

    /* =====================================================
     * 🥇 난이도별 랭킹 조회
     * ===================================================== */

    public List<AlgoRankDto> getTopNByDifficulty(String difficulty, int limit) {
        String key = buildDifficultyRankKey(LocalDate.now(), difficulty);

        Set<ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, limit - 1);

        log.info("📊 REDIS RANK FETCH | key={}", key);

        return buildRankResult(tuples);
    }

    public List<AlgoRankDto> getTop5ByDifficulty(String difficulty) {
        return getTopNByDifficulty(difficulty, TOP_N);
    }

    /* =====================================================
     * ⭐ 오늘 전체 랭킹 조회 (Master)
     * ===================================================== */

    public List<AlgoRankDto> getTodayTopN(int limit) {
        String key = buildDailyRankKey(LocalDate.now());

        Set<ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, limit - 1);

        log.info("📊 REDIS DAILY RANK FETCH | key={}", key);

        return buildRankResult(tuples);
    }

    public List<AlgoRankDto> getTodayTop5() {
        return getTodayTopN(TOP_N);
    }
}

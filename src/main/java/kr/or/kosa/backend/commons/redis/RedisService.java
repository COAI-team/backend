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

    private final RedisTemplate<String, Object> redisTemplate; // ✅ 제네릭 고정
    private final UserMapper userMapper;

    private static final int TOP_N = 5;
    private static final Duration RANK_TTL = Duration.ofDays(2);

    /** ✅ 키 생성 (한군데서만 만들기) */
    private String buildRankKey(LocalDate date, String difficulty) {
        return String.format("algo:rank:%s:%s", date, difficulty.trim().toUpperCase());
    }

    /** ✅ 랭킹 저장 (ZSET) : member=userId, score=finalScore */
    public void setAlgoRank(long userId, String problemDifficulty, double finalScore) {
        String key = buildRankKey(LocalDate.now(), problemDifficulty);

        redisTemplate.opsForZSet().add(key, String.valueOf(userId), finalScore);
        redisTemplate.expire(key, RANK_TTL);

        log.info("✅ ZSET ADD key={}, member={}, score={}", key, userId, finalScore);
    }

    /** ✅ 상위 N명 조회 */
    public List<AlgoRankDto> getTopN(String difficulty, int limit) {
        String key = buildRankKey(LocalDate.now(), difficulty);

        Set<ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        // ✅ null/empty 먼저 처리
        if (tuples == null || tuples.isEmpty()) {
            log.info("⚠️ ZSET EMPTY key={}", key);
            return List.of();
        }

        // ✅ userIds 추출 (null 안전)
        List<Long> userIds = tuples.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .map(Long::parseLong)
            .toList();

        if (userIds.isEmpty()) {
            return List.of();
        }

        // ✅ DB에서 닉네임 조회 (null 안전 + Map 변환)
        Map<Long, String> nicknameMap = userMapper.findNicknamesByIds(userIds).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                Users::getUserId,
                Users::getUserNickname,
                (a, b) -> a // 중복 키 방지
            ));

        // ✅ rank까지 포함해서 반환 (Unknown 방지)
        int rank = 0;
        List<AlgoRankDto> result = new ArrayList<>();

        for (ZSetOperations.TypedTuple<Object> t : tuples) {
            if (t.getValue() == null) continue;

            long uid = Long.parseLong(t.getValue().toString());
            double score = (t.getScore() == null) ? 0.0 : t.getScore();

            rank++;
            result.add(new AlgoRankDto(
                rank,
                uid,
                nicknameMap.getOrDefault(uid, "Unknown"),
                score
            ));
        }

        return result;
    }

    /** ✅ 상위 5명 */
    public List<AlgoRankDto> getTop5(String difficulty) {
        return getTopN(difficulty, TOP_N);
    }
}

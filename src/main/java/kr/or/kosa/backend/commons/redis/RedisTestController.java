package kr.or.kosa.backend.commons.redis;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class RedisTestController {

    private final RedisService redisService;

    @GetMapping("/test")
    public List<AlgoRankDto> test(
        @RequestParam long userId,
        @RequestParam String problemDifficulty,
        @RequestParam double submissionScore
    ) {
        // 1️⃣ 랭킹 저장
        redisService.setAlgoRank(userId, problemDifficulty, submissionScore);

        String key = String.format(
            "algo:rank:%s:%s",
            LocalDate.now(),
            problemDifficulty.toUpperCase()
        );

        System.out.println("🔑 Redis Key = " + key);

        // 2️⃣ 상위 5명 조회
        return redisService.getTop5(problemDifficulty);
    }
}

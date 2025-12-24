package kr.or.kosa.backend.codeboard.controller;

import kr.or.kosa.backend.codeboard.dto.PopularCodeboardResponseDto;
import kr.or.kosa.backend.codeboard.service.PopularCodeboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/popular/codeboard")
@RequiredArgsConstructor
public class PopularCodeboardController {

    private final PopularCodeboardService popularCodeboardService;

    @GetMapping
    public ResponseEntity<List<PopularCodeboardResponseDto>> getWeeklyPopularPosts() {
        List<PopularCodeboardResponseDto> posts = popularCodeboardService.getWeeklyPopularPosts();
        return ResponseEntity.ok(posts);
    }

    // 테스트를 위해 수동으로 배치를 실행할 수 있는 API
    @PostMapping("/batch")
    public ResponseEntity<String> runBatch() {
        try {
            popularCodeboardService.processWeeklyPopularPosts();
            return ResponseEntity.ok("코드게시판 배치 완료");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("배치 실패: " + e.getMessage());
        }
    }
}
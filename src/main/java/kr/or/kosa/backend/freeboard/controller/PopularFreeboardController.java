package kr.or.kosa.backend.freeboard.controller;

import kr.or.kosa.backend.freeboard.dto.PopularFreeboardResponseDto;
import kr.or.kosa.backend.freeboard.service.PopularFreeboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/popular/freeboard")
@RequiredArgsConstructor
public class PopularFreeboardController {

    private final PopularFreeboardService popularFreeboardService;

    @GetMapping
    public ResponseEntity<List<PopularFreeboardResponseDto>> getWeeklyPopularPosts() {
        List<PopularFreeboardResponseDto> posts = popularFreeboardService.getWeeklyPopularPosts();
        return ResponseEntity.ok(posts);
    }

    // 테스트를 위해 수동으로 배치를 실행할 수 있는 API
    @PostMapping("/batch")
    public ResponseEntity<String> runBatch() {
        try {
            popularFreeboardService.processWeeklyPopularPosts();
            return ResponseEntity.ok("자유게시판 배치 완료");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("배치 실패: " + e.getMessage());
        }
    }
}
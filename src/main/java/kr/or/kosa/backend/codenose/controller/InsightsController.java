package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.service.InsightsService;
import kr.or.kosa.backend.codenose.service.WordCloudService;
import lombok.RequiredArgsConstructor;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 인사이트 컨트롤러 (InsightsController)
 * 
 * 역할:
 * 사용자에게 분석 결과에 대한 통계, 트렌드, 시각화(WordCloud) 데이터를 제공합니다.
 * 'CodeNose'라는 이름처럼, 코드에서 발견된 '냄새'들의 패턴을 분석하여 대시보드 형태로 보여줍니다.
 */
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;
    private final WordCloudService wordCloudService;
    private final kr.or.kosa.backend.codenose.service.AnalysisService analysisService;

    /**
     * 분석 이력 조회
     * 
     * @param userId 사용자 ID
     * @return 최근 분석 수행 이력 리스트
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<CodeResultDTO>> getAnalysisHistory(@PathVariable Long userId) {
        List<CodeResultDTO> history = insightsService.getCodeResult(userId);
        return ResponseEntity.ok(history);
    }

    /**
     * 사용자 코드 패턴 조회
     * 
     * 자주 발생하는 Code Smell 유형과 빈도를 조회합니다.
     * 막대 그래프 등을 그릴 때 사용됩니다.
     */
    @GetMapping("/patterns/{userId}")
    public ResponseEntity<List<UserCodePatternDTO>> getUserPatterns(@PathVariable Long userId) {
        List<UserCodePatternDTO> patterns = insightsService.getUserCodePatterns(userId);
        return ResponseEntity.ok(patterns);
    }

    /**
     * 월별 트렌드 조회
     * 
     * 월별로 어떤 패턴이 자주 등장했는지 통계 데이터를 제공합니다.
     */
    @GetMapping("/trends/{userId}")
    public ResponseEntity<java.util.Map<String, Object>> getPatternTrends(@PathVariable Long userId) {
        return ResponseEntity.ok(insightsService.getPatternTrends(userId));
    }

    /**
     * 특정 패턴 상세 조회
     * 
     * "이 패턴이 어디서 발견되었지?"를 확인하기 위해
     * 특정 Code Smell 패턴이 검출된 파일 목록과 상세 내용을 반환합니다.
     */
    @GetMapping("/patterns/{userId}/detail")
    public ResponseEntity<List<java.util.Map<String, Object>>> getPatternDetails(
            @PathVariable Long userId,
            @RequestParam String pattern) {
        return ResponseEntity.ok(insightsService.getPatternDetails(userId, pattern));
    }

    /**
     * 워드 클라우드 생성
     * 
     * 사용자의 코드 스타일/자주 쓰이는 패턴을 워드 클라우드 이미지(Base64)로 생성하여 반환합니다.
     * 
     * @param userDetails 현재 로그인한 사용자 정보 (ID 추출용)
     * @param year        조회할 연도
     * @param month       조회할 월
     * @return Base64 인코딩된 PNG 이미지 데이터
     */
    @GetMapping("/wordcloud")
    public ResponseEntity<String> getWordCloud(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        String base64Image = wordCloudService.generateWordCloud(userDetails.id(), year, month);
        if (base64Image == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(base64Image);
    }

    /**
     * 메타데이터 백필 (관리자/개발용)
     * 
     * 기존 데이터에 빠진 메타데이터가 있다면 채워넣는 유틸리티성 API입니다.
     */
    @PostMapping("/metadata/backfill")
    public ResponseEntity<String> runMetadataBackfill() {
        int count = analysisService.runMetadataBackfill();
        return ResponseEntity.ok("Metadata backfill completed. Updated " + count + " records.");
    }
}

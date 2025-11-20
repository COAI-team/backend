package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.AnalysisRequestDto;
// import kr.or.kosa.backend.codenose.service.AnalysisService; // Commented out
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    // private final AnalysisService analysisService; // Commented out

    @PostMapping("/analyze")
    public ResponseEntity<String> analyzeCode(@RequestBody AnalysisRequestDto requestDto) {
        // In a real app, you would get the userId from the security context
        // For example:
        // Long userId = ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        // requestDto.setUserId(userId);
        
        // String result = analysisService.analyzeCode(requestDto); // Original call
        String dummyResult = """
            {
              "aiScore": 75,
              "codeSmells": [
                {
                  "name": "Dummy Long Method",
                  "description": "This is a dummy code smell for a long method. Please implement the actual AI."
                },
                {
                  "name": "Dummy Magic Number",
                  "description": "This is a dummy code smell for a magic number. Authenticate users properly."
                }
              ],
              "suggestions": [
                {
                  "problematicSnippet": "Dummy problematic code",
                  "proposedReplacement": "Dummy suggested replacement"
                }
              ]
            }
            """;
        return ResponseEntity.ok(dummyResult);
    }
}

package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.RagDto;
import kr.or.kosa.backend.codenose.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG(Retrieval-Augmented Generation) 컨트롤러 (RagController)
 * 
 * 역할:
 * LLM(Large Language Model)이 더 정확한 답변을 할 수 있도록, 외부 지식(코드베이스 등)을
 * 벡터 데이터베이스에 저장(Ingest)하고, 이를 바탕으로 질문에 답변(Feedback)하는 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * 코드 수집 및 벡터화 (Ingest)
     * 
     * 사용자의 코드를 청크(Chunk)로 나누고 임베딩(Embedding)하여 벡터 DB에 저장합니다.
     * 추후 질문 시 이 벡터들을 검색하여 문맥(Context)으로 활용합니다.
     * 
     * @param request 수집할 텍스트 및 메타데이터
     */
    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody RagDto.IngestRequest request) {
        ragService.ingestCode(request);
        return ResponseEntity.ok().build();
    }

    /**
     * RAG 기반 피드백/질문 답변
     * 
     * 사용자의 질문과 유사한 코드 조각을 벡터 DB에서 검색한 뒤,
     * 이를 LLM 프롬프트에 포함시켜 "내 코드에 기반한" 정확한 답변을 생성합니다.
     * 
     * @param request 사용자의 질문 내용
     * @return AI의 답변
     */
    @PostMapping("/feedback")
    public ResponseEntity<RagDto.FeedbackResponse> getFeedback(@RequestBody RagDto.FeedbackRequest request) {
        String answer = ragService.getPersonalizedFeedback(request);
        return ResponseEntity.ok(new RagDto.FeedbackResponse(answer));
    }
}

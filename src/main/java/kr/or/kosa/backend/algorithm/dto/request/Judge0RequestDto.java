package kr.or.kosa.backend.algorithm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Judge0 API 요청 DTO
 *
 * 외부 API 전송용 DTO: 서비스에서 빌더로 생성, JSON 직렬화
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용
 * - @Getter: Jackson이 JSON 직렬화
 */
@Getter
@Builder
@AllArgsConstructor
public class Judge0RequestDto {

    private String source_code;
    private Integer language_id;
    private String stdin;
    private String expected_output;
    private Float cpu_time_limit;
    private Integer memory_limit;
    private Boolean enable_per_process_and_thread_time_limit;
    private Boolean enable_per_process_and_thread_memory_limit;
}
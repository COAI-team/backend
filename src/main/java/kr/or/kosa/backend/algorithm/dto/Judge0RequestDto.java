package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Judge0 API 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Judge0RequestDto {

    private String source_code;
    private Integer language_id;
    private String stdin;
    private String expected_output;
    private Integer cpu_time_limit;
    private Integer memory_limit;
    private Boolean enable_per_process_and_thread_time_limit;
    private Boolean enable_per_process_and_thread_memory_limit;
}
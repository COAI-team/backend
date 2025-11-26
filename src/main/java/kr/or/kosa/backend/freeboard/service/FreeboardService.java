package kr.or.kosa.backend.freeboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreeboardService {

    private final FreeboardMapper mapper;
    private final ObjectMapper objectMapper;

    // 페이지 단위 목록 조회
    public Map<String, Object> listPage(int page, int size) {
        int offset = (page - 1) * size;

        List<Freeboard> boards = mapper.selectPage(offset, size);
        int totalCount = mapper.countAll();

        Map<String, Object> result = new HashMap<>();
        result.put("boards", boards);
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) totalCount / size));

        return result;
    }

    // 상세 조회 (조회수 증가 포함)
    @Transactional
    public Freeboard detail(Long id) {
        mapper.increaseClick(id);

        Freeboard freeboard = mapper.selectById(id);
        if (freeboard == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }

        return freeboard;
    }

    // 게시글 작성
    @Transactional
    public Long write(FreeboardDto dto, Long userId) {

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
            throw new RuntimeException("게시글 작성 중 오류가 발생했습니다.", e);
        }

        Freeboard freeboard = Freeboard.builder()
                .userId(userId)
                .freeboardTitle(dto.getFreeboardTitle())
                .freeboardContent(jsonContent)
                .freeboardPlainText(plainText)
                .freeboardRepresentImage(dto.getFreeboardRepresentImage())
                .freeboardDeletedYn("N")
                .build();

        int inserted = mapper.insert(freeboard);
        if (inserted == 0) {
            throw new RuntimeException("게시글 작성에 실패했습니다.");
        }

        return freeboard.getFreeboardId();
    }


    // 게시글 수정
    @Transactional
    public void edit(Long id, FreeboardDto dto, Long userId) {

        Freeboard existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new SecurityException("게시글 수정 권한이 없습니다.");
        }

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패: freeboardId={}", id, e);
            throw new RuntimeException("게시글 수정 중 오류가 발생했습니다.", e);
        }

        Freeboard freeboard = Freeboard.builder()
                .freeboardId(id)   // 기존 ID 유지
                .freeboardTitle(dto.getFreeboardTitle())
                .freeboardContent(jsonContent)
                .freeboardPlainText(plainText)
                .freeboardRepresentImage(dto.getFreeboardRepresentImage())
                .build();

        if (mapper.update(freeboard) == 0) {
            throw new RuntimeException("게시글 수정에 실패했습니다.");
        }
    }


    // 게시글 삭제 (소프트 삭제)
    @Transactional
    public void delete(Long id, Long userId) {
        Freeboard existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new SecurityException("게시글 삭제 권한이 없습니다.");
        }

        int result = mapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("게시글 삭제에 실패했습니다.");
        }
    }
}
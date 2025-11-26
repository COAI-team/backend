package kr.or.kosa.backend.freeboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.exception.FreeboardErrorCode;
import kr.or.kosa.backend.freeboard.exception.FreeboardException;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import kr.or.kosa.backend.tag.service.TagService;
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
    private final TagService tagService;

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

    @Transactional
    public Freeboard detail(Long id) {
        mapper.increaseClick(id);

        Freeboard freeboard = mapper.selectById(id);
        if (freeboard == null) {
            throw new FreeboardException(FreeboardErrorCode.NOT_FOUND);
        }

        List<String> tags = tagService.getFreeboardTags(id);
        freeboard.setTags(tags);

        return freeboard;
    }

    @Transactional
    public Long write(FreeboardDto dto, Long userId) {

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
            throw new FreeboardException(FreeboardErrorCode.JSON_PARSE_ERROR);
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
            throw new FreeboardException(FreeboardErrorCode.INSERT_ERROR);
        }

        Long freeboardId = freeboard.getFreeboardId();

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            tagService.attachTagsToFreeboard(freeboardId, dto.getTags());
        }

        return freeboardId;
    }

    @Transactional
    public void edit(Long id, FreeboardDto dto, Long userId) {

        Freeboard existing = mapper.selectById(id);
        if (existing == null) {
            throw new FreeboardException(FreeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new FreeboardException(FreeboardErrorCode.NO_EDIT_PERMISSION);
        }

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패: freeboardId={}", id, e);
            throw new FreeboardException(FreeboardErrorCode.JSON_PARSE_ERROR);
        }

        Freeboard freeboard = Freeboard.builder()
                .freeboardId(id)
                .freeboardTitle(dto.getFreeboardTitle())
                .freeboardContent(jsonContent)
                .freeboardPlainText(plainText)
                .freeboardRepresentImage(dto.getFreeboardRepresentImage())
                .build();

        if (mapper.update(freeboard) == 0) {
            throw new FreeboardException(FreeboardErrorCode.UPDATE_ERROR);
        }

        if (dto.getTags() != null) {
            tagService.updateFreeboardTags(id, dto.getTags());
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Freeboard existing = mapper.selectById(id);
        if (existing == null) {
            throw new FreeboardException(FreeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new FreeboardException(FreeboardErrorCode.NO_DELETE_PERMISSION);
        }

        int result = mapper.delete(id);
        if (result == 0) {
            throw new FreeboardException(FreeboardErrorCode.DELETE_ERROR);
        }
    }
}
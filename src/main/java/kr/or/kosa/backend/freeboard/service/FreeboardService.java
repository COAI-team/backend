package kr.or.kosa.backend.freeboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDetailResponseDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardListResponseDto;
import kr.or.kosa.backend.freeboard.exception.FreeboardErrorCode;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.mapper.LikeMapper;
import kr.or.kosa.backend.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    private final LikeMapper likeMapper;

    public Map<String, Object> listPage(int page, int size) {
        int offset = (page - 1) * size;

        List<FreeboardListResponseDto> boards = mapper.selectPage(offset, size);
        int totalCount = mapper.countAll();

        Map<String, Object> result = new HashMap<>();
        result.put("boards", boards);
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) totalCount / size));

        return result;
    }

    public Page<FreeboardListResponseDto> getList(Pageable pageable, String keyword) {
        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();
        String sortField = getSortField(pageable);
        String sortDirection = getSortDirection(pageable);

        int totalCount = mapper.countPosts(keyword);

        List<FreeboardListResponseDto> posts = mapper.findPosts(
                offset,
                pageSize,
                sortField,
                sortDirection,
                keyword
        );

        return new PageImpl<>(posts, pageable, totalCount);
    }

    private String getSortField(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "created_at";
        }

        String property = pageable.getSort().iterator().next().getProperty();

        switch (property) {
            case "commentCount":
                return "comment_count";
            case "likeCount":
                return "like_count";
            case "viewCount":
            case "click":
                return "view_count";
            case "createdAt":
            default:
                return "created_at";
        }
    }

    private String getSortDirection(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "DESC";
        }

        return pageable.getSort().iterator().next().getDirection().name();
    }

    @Transactional
    public FreeboardDetailResponseDto detail(Long id, Long userId) {
        mapper.increaseClick(id);

        FreeboardDetailResponseDto freeboard = mapper.selectById(id);
        if (freeboard == null) {
            throw new CustomBusinessException(FreeboardErrorCode.NOT_FOUND);
        }

        List<String> tags = tagService.getFreeboardTags(id);
        Like existingLike = likeMapper.selectLike(userId, ReferenceType.POST_FREEBOARD, id);
        boolean isLiked = existingLike != null;

        return FreeboardDetailResponseDto.builder()
                .freeboardId(freeboard.getFreeboardId())
                .userId(freeboard.getUserId())
                .userNickname(freeboard.getUserNickname())
                .freeboardTitle(freeboard.getFreeboardTitle())
                .freeboardContent(freeboard.getFreeboardContent())
                .freeboardClick(freeboard.getFreeboardClick())
                .likeCount(freeboard.getLikeCount() != null ? freeboard.getLikeCount() : 0)
                .freeboardImage(freeboard.getFreeboardImage())
                .freeboardRepresentImage(freeboard.getFreeboardRepresentImage())
                .freeboardCreatedAt(freeboard.getFreeboardCreatedAt())
                .tags(tags)
                .isLiked(isLiked)
                .build();
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
            throw new CustomBusinessException(FreeboardErrorCode.JSON_PARSE_ERROR);
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
            throw new CustomBusinessException(FreeboardErrorCode.INSERT_ERROR);
        }

        Long freeboardId = freeboard.getFreeboardId();

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            tagService.attachTagsToFreeboard(freeboardId, dto.getTags());
        }

        return freeboardId;
    }

    @Transactional
    public void edit(Long id, FreeboardDto dto, Long userId) {
        FreeboardDetailResponseDto existing = mapper.selectById(id);
        if (existing == null) {
            throw new CustomBusinessException(FreeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(FreeboardErrorCode.NO_EDIT_PERMISSION);
        }

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패: freeboardId={}", id, e);
            throw new CustomBusinessException(FreeboardErrorCode.JSON_PARSE_ERROR);
        }

        Freeboard freeboard = Freeboard.builder()
                .freeboardId(id)
                .freeboardTitle(dto.getFreeboardTitle())
                .freeboardContent(jsonContent)
                .freeboardPlainText(plainText)
                .freeboardRepresentImage(dto.getFreeboardRepresentImage())
                .build();

        if (mapper.update(freeboard) == 0) {
            throw new CustomBusinessException(FreeboardErrorCode.UPDATE_ERROR);
        }

        if (dto.getTags() != null) {
            tagService.updateFreeboardTags(id, dto.getTags());
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        FreeboardDetailResponseDto existing = mapper.selectById(id);
        if (existing == null) {
            throw new CustomBusinessException(FreeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(FreeboardErrorCode.NO_DELETE_PERMISSION);
        }

        int result = mapper.delete(id);
        if (result == 0) {
            throw new CustomBusinessException(FreeboardErrorCode.DELETE_ERROR);
        }
    }
}
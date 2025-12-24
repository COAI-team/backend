package kr.or.kosa.backend.codeboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codeboard.domain.Codeboard;
import kr.or.kosa.backend.codeboard.dto.CodeboardDetailResponseDto;
import kr.or.kosa.backend.codeboard.dto.CodeboardDto;
import kr.or.kosa.backend.codeboard.dto.CodeboardListResponseDto;
import kr.or.kosa.backend.codeboard.exception.CodeboardErrorCode;
import kr.or.kosa.backend.codeboard.mapper.CodeboardMapper;
import kr.or.kosa.backend.codeboard.sort.CodeboardSortType;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.pagination.PageRequest;
import kr.or.kosa.backend.commons.pagination.PageResponse;
import kr.or.kosa.backend.commons.pagination.SearchCondition;
import kr.or.kosa.backend.commons.pagination.SortCondition;
import kr.or.kosa.backend.commons.pagination.SortDirection;
import kr.or.kosa.backend.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeboardService {

    private final CodeboardMapper mapper;
    private final ObjectMapper objectMapper;
    private final TagService tagService;

    public PageResponse<CodeboardListResponseDto> getList(
            int page,
            int size,
            CodeboardSortType sortType,
            SortDirection direction,
            String keyword
    ) {
        PageRequest pageRequest = new PageRequest(page, size);
        SearchCondition searchCondition = new SearchCondition(keyword);
        SortCondition sortCondition = new SortCondition(sortType, direction);

        List<CodeboardListResponseDto> boards =
                mapper.findPosts(pageRequest, searchCondition, sortCondition);

        List<Long> boardIds = boards.stream()
                .map(CodeboardListResponseDto::getCodeboardId)
                .collect(Collectors.toList());

        List<CodeboardListResponseDto> boardsWithTags = boards;
        if (!boardIds.isEmpty()) {
            Map<Long, List<String>> tagsMap = tagService.getCodeboardTagsMap(boardIds);

            boardsWithTags = boards.stream()
                    .map(board -> {
                        List<String> tags = tagsMap.getOrDefault(board.getCodeboardId(), new ArrayList<>());
                        return CodeboardListResponseDto.builder()
                                .codeboardId(board.getCodeboardId())
                                .userId(board.getUserId())
                                .userNickname(board.getUserNickname())
                                .userImage(board.getUserImage())
                                .analysisId(board.getAnalysisId())
                                .codeboardTitle(board.getCodeboardTitle())
                                .codeboardSummary(board.getCodeboardSummary())
                                .codeboardClick(board.getCodeboardClick())
                                .codeboardCreatedAt(board.getCodeboardCreatedAt())
                                .likeCount(board.getLikeCount())
                                .commentCount(board.getCommentCount())
                                .aiScore(board.getAiScore())
                                .tags(tags)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        long totalCount = mapper.countPosts(searchCondition);

        return new PageResponse<>(boardsWithTags, pageRequest, totalCount);
    }

    @Transactional
    public Long write(CodeboardDto dto, Long userId) {
        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패", e);
            throw new CustomBusinessException(CodeboardErrorCode.JSON_PARSE_ERROR);
        }

        Codeboard codeboard = Codeboard.builder()
                .userId(userId)
                .analysisId(dto.getAnalysisId())
                .codeboardTitle(dto.getCodeboardTitle())
                .codeboardBlocks(jsonContent)
                .codeboardPlainText(plainText)
                .codeboardDeletedYn("N")
                .build();

        int inserted = mapper.insert(codeboard);
        if (inserted == 0) {
            throw new CustomBusinessException(CodeboardErrorCode.INSERT_ERROR);
        }

        Long codeboardId = codeboard.getCodeboardId();

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            tagService.attachTagsToCodeboard(codeboardId, dto.getTags());
        }

        return codeboardId;
    }

    @Transactional
    public CodeboardDetailResponseDto detail(Long id, Long userId) {
        mapper.increaseClick(id);

        CodeboardDetailResponseDto codeboard = mapper.selectById(id, userId);
        if (codeboard == null) {
            throw new CustomBusinessException(CodeboardErrorCode.NOT_FOUND);
        }

        List<String> tags = tagService.getCodeboardTags(id);

        return CodeboardDetailResponseDto.builder()
                .codeboardId(codeboard.getCodeboardId())
                .userId(codeboard.getUserId())
                .userNickname(codeboard.getUserNickname())
                .userImage(codeboard.getUserImage())
                .analysisId(codeboard.getAnalysisId())
                .codeboardTitle(codeboard.getCodeboardTitle())
                .codeboardContent(codeboard.getCodeboardContent())
                .codeboardClick(codeboard.getCodeboardClick())
                .likeCount(codeboard.getLikeCount() != null ? codeboard.getLikeCount() : 0)
                .commentCount(codeboard.getCommentCount() != null ? codeboard.getCommentCount() : 0)
                .isLiked(codeboard.getIsLiked() != null ? codeboard.getIsLiked() : false)
                .codeboardCreatedAt(codeboard.getCodeboardCreatedAt())
                .tags(tags)
                .build();
    }

    @Transactional
    public void edit(Long id, CodeboardDto dto, Long userId) {
        CodeboardDetailResponseDto existing = mapper.selectById(id, null);
        if (existing == null) {
            throw new CustomBusinessException(CodeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(CodeboardErrorCode.NO_EDIT_PERMISSION);
        }

        String jsonContent;
        String plainText;

        try {
            jsonContent = dto.toJsonContent(objectMapper);
            plainText = dto.toPlainText(objectMapper);
        } catch (Exception e) {
            log.error("JSON 변환 실패: codeboardId={}", id, e);
            throw new CustomBusinessException(CodeboardErrorCode.JSON_PARSE_ERROR);
        }

        Codeboard codeboard = Codeboard.builder()
                .codeboardId(id)
                .analysisId(dto.getAnalysisId())
                .codeboardTitle(dto.getCodeboardTitle())
                .codeboardBlocks(jsonContent)
                .codeboardPlainText(plainText)
                .build();

        if (mapper.update(codeboard) == 0) {
            throw new CustomBusinessException(CodeboardErrorCode.UPDATE_ERROR);
        }

        if (dto.getTags() != null) {
            tagService.updateCodeboardTags(id, dto.getTags());
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        CodeboardDetailResponseDto existing = mapper.selectById(id, null);
        if (existing == null) {
            throw new CustomBusinessException(CodeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(CodeboardErrorCode.NO_DELETE_PERMISSION);
        }

        int result = mapper.delete(id);
        if (result == 0) {
            throw new CustomBusinessException(CodeboardErrorCode.DELETE_ERROR);
        }
    }
}
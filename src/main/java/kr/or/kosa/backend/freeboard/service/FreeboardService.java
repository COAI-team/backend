package kr.or.kosa.backend.freeboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.pagination.PageRequest;
import kr.or.kosa.backend.commons.pagination.PageResponse;
import kr.or.kosa.backend.commons.pagination.SearchCondition;
import kr.or.kosa.backend.commons.pagination.SortCondition;
import kr.or.kosa.backend.commons.pagination.SortDirection;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDetailResponseDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardListResponseDto;
import kr.or.kosa.backend.freeboard.exception.FreeboardErrorCode;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import kr.or.kosa.backend.freeboard.sort.FreeboardSortType;
import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.mapper.LikeMapper;
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
public class FreeboardService {

    private final FreeboardMapper mapper;
    private final ObjectMapper objectMapper;
    private final TagService tagService;
    private final LikeMapper likeMapper;

    // 자유게시판 목록 조회 (검색 + 정렬 + 페이지네이션)
    public PageResponse<FreeboardListResponseDto> getList(
            int page,
            int size,
            FreeboardSortType sortType,
            SortDirection direction,
            String keyword
    ) {
        PageRequest pageRequest = new PageRequest(page, size);
        SearchCondition searchCondition = new SearchCondition(keyword);
        SortCondition sortCondition = new SortCondition(sortType, direction);

        List<FreeboardListResponseDto> boards =
                mapper.findPosts(pageRequest, searchCondition, sortCondition);

        // 게시글 ID 목록 추출
        List<Long> boardIds = boards.stream()
                .map(FreeboardListResponseDto::getFreeboardId)
                .collect(Collectors.toList());

        // 태그 일괄 조회 (N+1 방지)
        List<FreeboardListResponseDto> boardsWithTags = boards;
        if (!boardIds.isEmpty()) {
            Map<Long, List<String>> tagsMap = tagService.getFreeboardTagsMap(boardIds);

            // 불변 객체 유지하면서 태그 추가된 새 객체 생성
            boardsWithTags = boards.stream()
                    .map(board -> {
                        List<String> tags = tagsMap.getOrDefault(board.getFreeboardId(), new ArrayList<>());
                        return FreeboardListResponseDto.builder()
                                .freeboardId(board.getFreeboardId())
                                .userId(board.getUserId())
                                .userNickname(board.getUserNickname())
                                .freeboardTitle(board.getFreeboardTitle())
                                .freeboardSummary(board.getFreeboardSummary())
                                .freeboardRepresentImage(board.getFreeboardRepresentImage())
                                .freeboardClick(board.getFreeboardClick())
                                .likeCount(board.getLikeCount())
                                .commentCount(board.getCommentCount())
                                .tags(tags)
                                .freeboardCreatedAt(board.getFreeboardCreatedAt())
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        long totalCount = mapper.countPosts(searchCondition);

        return new PageResponse<>(boardsWithTags, pageRequest, totalCount);
    }

    @Transactional
    public FreeboardDetailResponseDto detail(Long id, Long userId) {
        mapper.increaseClick(id);

        FreeboardDetailResponseDto freeboard = mapper.selectById(id);
        if (freeboard == null) {
            throw new CustomBusinessException(FreeboardErrorCode.NOT_FOUND);
        }

        log.info("조회된 좋아요 수: {}", freeboard.getLikeCount());  // 디버깅 로그

        List<String> tags = tagService.getFreeboardTags(id);

        boolean isLiked = false;
        if (userId != null) {
            Like existingLike = likeMapper.selectLike(userId, ReferenceType.POST_FREEBOARD, id);
            isLiked = existingLike != null;
            log.info("사용자 {}의 좋아요 여부: {}", userId, isLiked);  // 디버깅 로그
        }

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
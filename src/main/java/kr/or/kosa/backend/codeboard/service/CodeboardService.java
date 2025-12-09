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
import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.mapper.LikeMapper;
import kr.or.kosa.backend.tag.service.TagService;
import kr.or.kosa.backend.toolbar.block.BlockJsonConverter;
import kr.or.kosa.backend.toolbar.block.BlockSecurityGuard;
import kr.or.kosa.backend.toolbar.block.BlockShape;
import kr.or.kosa.backend.toolbar.block.BlockTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeboardService {

    private final CodeboardMapper mapper;
    private final ObjectMapper objectMapper;
    private final TagService tagService;
    private final LikeMapper likeMapper;

    // 코드 게시판 목록 조회 (검색 + 정렬 + 페이지네이션)
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

        long totalCount = mapper.countPosts(searchCondition);

        return new PageResponse<>(boards, pageRequest, totalCount);
    }

    // 코드 게시글 작성
    @Transactional
    public Long write(CodeboardDto dto, Long userId) {
        // 블록 변환
        List<BlockShape> blocks;
        try {
            blocks = BlockJsonConverter.toBlockList(dto.getBlocks(), objectMapper);
        } catch (Exception e) {
            log.error("블록 변환 실패", e);
            throw new CustomBusinessException(CodeboardErrorCode.JSON_PARSE_ERROR);
        }

        // 보안 검증
        try {
            BlockSecurityGuard.guard(blocks, objectMapper);
        } catch (IllegalArgumentException e) {
            log.warn("보안 검증 실패: {}", e.getMessage());
            throw new CustomBusinessException(CodeboardErrorCode.INVALID_BLOCK_CONTENT);
        }

        // JSON 직렬화 및 플레인 텍스트 추출
        String jsonContent;
        String plainText;
        try {
            jsonContent = objectMapper.writeValueAsString(blocks);
            plainText = BlockTextExtractor.extractPlainText(dto.getCodeboardTitle(), blocks, objectMapper);
        } catch (Exception e) {
            log.error("JSON 직렬화 실패", e);
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

    // 코드 게시글 상세 조회
    @Transactional
    public CodeboardDetailResponseDto detail(Long id, Long userId) {
        mapper.increaseClick(id);

        CodeboardDetailResponseDto codeboard = mapper.selectById(id);
        if (codeboard == null) {
            throw new CustomBusinessException(CodeboardErrorCode.NOT_FOUND);
        }

        log.info("조회된 좋아요 수: {}", codeboard.getLikeCount());

        List<String> tags = tagService.getCodeboardTags(id);

        boolean isLiked = false;
        if (userId != null) {
            Like existingLike = likeMapper.selectLike(userId, ReferenceType.POST_CODEBOARD, id);
            isLiked = existingLike != null;
            log.info("사용자 {}의 좋아요 여부: {}", userId, isLiked);
        }

        return CodeboardDetailResponseDto.builder()
                .codeboardId(codeboard.getCodeboardId())
                .userId(codeboard.getUserId())
                .userNickname(codeboard.getUserNickname())
                .analysisId(codeboard.getAnalysisId())
                .codeboardTitle(codeboard.getCodeboardTitle())
                .codeboardContent(codeboard.getCodeboardContent())
                .codeboardClick(codeboard.getCodeboardClick())
                .likeCount(codeboard.getLikeCount() != null ? codeboard.getLikeCount() : 0)
                .codeboardCreatedAt(codeboard.getCodeboardCreatedAt())
                .tags(tags)
                .isLiked(isLiked)
                .build();
    }

    // 코드 게시글 수정
    @Transactional
    public void edit(Long id, CodeboardDto dto, Long userId) {
        CodeboardDetailResponseDto existing = mapper.selectById(id);
        if (existing == null) {
            throw new CustomBusinessException(CodeboardErrorCode.NOT_FOUND);
        }
        if (!existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(CodeboardErrorCode.NO_EDIT_PERMISSION);
        }

        // 블록 변환
        List<BlockShape> blocks;
        try {
            blocks = BlockJsonConverter.toBlockList(dto.getBlocks(), objectMapper);
        } catch (Exception e) {
            log.error("블록 변환 실패: codeboardId={}", id, e);
            throw new CustomBusinessException(CodeboardErrorCode.JSON_PARSE_ERROR);
        }

        // 보안 검증
        try {
            BlockSecurityGuard.guard(blocks, objectMapper);
        } catch (IllegalArgumentException e) {
            log.warn("보안 검증 실패: codeboardId={}, {}", id, e.getMessage());
            throw new CustomBusinessException(CodeboardErrorCode.INVALID_BLOCK_CONTENT);
        }

        // JSON 직렬화 및 플레인 텍스트 추출
        String jsonContent;
        String plainText;
        try {
            jsonContent = objectMapper.writeValueAsString(blocks);
            plainText = BlockTextExtractor.extractPlainText(dto.getCodeboardTitle(), blocks, objectMapper);
        } catch (Exception e) {
            log.error("JSON 직렬화 실패: codeboardId={}", id, e);
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

    // 코드 게시글 삭제
    @Transactional
    public void delete(Long id, Long userId) {
        CodeboardDetailResponseDto existing = mapper.selectById(id);
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
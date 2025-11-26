package kr.or.kosa.backend.tag.service;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.tag.domain.CodeboardTag;
import kr.or.kosa.backend.tag.domain.FreeboardTag;
import kr.or.kosa.backend.tag.domain.Tag;
import kr.or.kosa.backend.tag.dto.TagAutocompleteDto;
import kr.or.kosa.backend.tag.exception.TagErrorCode;
import kr.or.kosa.backend.tag.mapper.CodeboardTagMapper;
import kr.or.kosa.backend.tag.mapper.FreeboardTagMapper;
import kr.or.kosa.backend.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagMapper tagMapper;
    private final CodeboardTagMapper codeboardTagMapper;
    private final FreeboardTagMapper freeboardTagMapper;

    // 태그 조회 또는 생성 (Validation은 이미 Request에서 완료됨)
    @Transactional
    public Tag getOrCreateTag(String tagInput) {
        String normalizedName = tagInput.toLowerCase().trim();

        // 기존 태그 조회
        Optional<Tag> existingTag = tagMapper.findByTagName(normalizedName);
        if (existingTag.isPresent()) {
            return existingTag.get();
        }

        // 새 태그 생성
        try {
            Tag newTag = Tag.builder()
                    .tagName(normalizedName)
                    .build();

            int result = tagMapper.insertTag(newTag);
            if (result == 0 || newTag.getTagId() == null) {
                throw new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED);
            }

            log.info("새 태그 생성: tagId={}, tagName={}", newTag.getTagId(), normalizedName);
            return newTag;

        } catch (DataIntegrityViolationException e) {
            // 동시성 문제로 UNIQUE 제약 위반 시 재조회
            return tagMapper.findByTagName(normalizedName)
                    .orElseThrow(() -> new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED));
        }
    }

    // 코드게시판 게시글에 태그 저장
    @Transactional
    public void attachTagsToCodeboard(Long codeboardId, List<String> tagInputs) {
        if (tagInputs == null || tagInputs.isEmpty()) {
            return;
        }

        for (String tagInput : tagInputs) {
            Tag tag = getOrCreateTag(tagInput.trim());

            CodeboardTag codeboardTag = CodeboardTag.builder()
                    .codeboardId(codeboardId)
                    .tagId(tag.getTagId())
                    .tagDisplayName(tagInput.trim())
                    .build();

            int result = codeboardTagMapper.insert(codeboardTag);
            if (result == 0) {
                throw new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED);
            }
        }

        log.info("코드게시판 태그 저장 완료: codeboardId={}, 태그 수={}", codeboardId, tagInputs.size());
    }

    // 자유게시판 게시글에 태그 저장
    @Transactional
    public void attachTagsToFreeboard(Long freeboardId, List<String> tagInputs) {
        log.info("=== attachTagsToFreeboard 시작 ===");

        if (tagInputs == null || tagInputs.isEmpty()) {
            log.warn("tagInputs가 null이거나 비어있음");
            return;
        }

        log.info("태그 개수: {}", tagInputs.size());
        for (String tagInput : tagInputs) {
            log.info("태그 처리 중: {}", tagInput);
            Tag tag = getOrCreateTag(tagInput.trim());
            log.info("태그 ID: {}", tag.getTagId());

            FreeboardTag freeboardTag = FreeboardTag.builder()
                    .freeboardId(freeboardId)
                    .tagId(tag.getTagId())
                    .tagDisplayName(tagInput.trim())
                    .build();

            int result = freeboardTagMapper.insert(freeboardTag);
            log.info("삽입 결과: {}", result);

            if (result == 0) {
                log.error("태그 삽입 실패!");
                throw new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED);
            }
        }

        log.info("자유게시판 태그 저장 완료: freeboardId={}, 태그 수={}", freeboardId, tagInputs.size());
    }

    // 코드게시판 게시글의 태그 조회
    public List<String> getCodeboardTags(Long codeboardId) {
        return codeboardTagMapper.findByCodeboardId(codeboardId)
                .stream()
                .map(CodeboardTag::getTagDisplayName)
                .toList();
    }

    // 자유게시판 게시글의 태그 조회
    public List<String> getFreeboardTags(Long freeboardId) {
        return freeboardTagMapper.findByFreeboardId(freeboardId)
                .stream()
                .map(FreeboardTag::getTagDisplayName)
                .toList();
    }

    // 코드게시판 게시글 태그 수정
    @Transactional
    public void updateCodeboardTags(Long codeboardId, List<String> tagInputs) {
        codeboardTagMapper.deleteByCodeboardId(codeboardId);

        if (tagInputs != null && !tagInputs.isEmpty()) {
            attachTagsToCodeboard(codeboardId, tagInputs);
        }
    }

    // 자유게시판 게시글 태그 수정
    @Transactional
    public void updateFreeboardTags(Long freeboardId, List<String> tagInputs) {
        log.info("=== updateFreeboardTags 시작 ===");
        log.info("freeboardId: {}", freeboardId);
        log.info("tagInputs: {}", tagInputs);

        log.info("기존 태그 삭제 시작");
        int deleted = freeboardTagMapper.deleteByFreeboardId(freeboardId);
        log.info("삭제된 태그 수: {}", deleted);

        if (tagInputs != null && !tagInputs.isEmpty()) {
            log.info("새로운 태그 저장 시작: {} 개", tagInputs.size());
            attachTagsToFreeboard(freeboardId, tagInputs);
            log.info("태그 저장 완료");
        } else {
            log.warn("저장할 태그가 없음 (null 또는 empty)");
        }

        log.info("=== updateFreeboardTags 완료 ===");
    }

    // 자동완성 검색
    public List<TagAutocompleteDto> searchTagsForAutocomplete(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        if (limit < 1 || limit > 20) {
            limit = 10;
        }

        String lowerKeyword = keyword.toLowerCase().trim();
        List<Tag> tags = tagMapper.findByTagNameStartingWith(lowerKeyword);

        return tags.stream()
                .limit(limit)
                .map(tag -> {
                    String popularDisplay = tagMapper.findMostUsedDisplayName(tag.getTagId());
                    if (popularDisplay == null || popularDisplay.isEmpty()) {
                        popularDisplay = tag.getTagName();
                    }

                    Long count = tagMapper.countByTagId(tag.getTagId());

                    return TagAutocompleteDto.builder()
                            .tagId(tag.getTagId())
                            .tagDisplayName(popularDisplay)
                            .count(count != null ? count : 0L)
                            .build();
                })
                .sorted(Comparator.comparing(TagAutocompleteDto::getCount).reversed())
                .toList();
    }

    // 태그로 코드게시판 게시글 검색
    public List<Long> searchCodeboardByTag(String tagDisplay) {
        if (tagDisplay == null || tagDisplay.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedTag = tagDisplay.toLowerCase().trim();

        Optional<Tag> tag = tagMapper.findByTagName(normalizedTag);
        if (tag.isEmpty()) {
            return new ArrayList<>();
        }

        return codeboardTagMapper.findByTagId(tag.get().getTagId())
                .stream()
                .map(CodeboardTag::getCodeboardId)
                .toList();
    }

    // 태그로 자유게시판 게시글 검색
    public List<Long> searchFreeboardByTag(String tagDisplay) {
        if (tagDisplay == null || tagDisplay.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedTag = tagDisplay.toLowerCase().trim();

        Optional<Tag> tag = tagMapper.findByTagName(normalizedTag);
        if (tag.isEmpty()) {
            return new ArrayList<>();
        }

        return freeboardTagMapper.findByTagId(tag.get().getTagId())
                .stream()
                .map(FreeboardTag::getFreeboardId)
                .toList();
    }
}
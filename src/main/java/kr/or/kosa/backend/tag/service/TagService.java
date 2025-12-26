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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagMapper tagMapper;
    private final CodeboardTagMapper codeboardTagMapper;
    private final FreeboardTagMapper freeboardTagMapper;

    @Transactional
    public Tag getOrCreateTag(String tagInput) {
        String normalizedName = tagInput.toLowerCase().trim();

        Optional<Tag> existingTag = tagMapper.findByTagName(normalizedName);
        if (existingTag.isPresent()) {
            return existingTag.get();
        }

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
            return tagMapper.findByTagName(normalizedName)
                    .orElseThrow(() -> new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED));
        }
    }

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

    @Transactional
    public void attachTagsToFreeboard(Long freeboardId, List<String> tagInputs) {
        if (tagInputs == null || tagInputs.isEmpty()) {
            log.info(">>> attachTagsToFreeboard: 태그 입력이 없음");
            return;
        }

        log.info(">>> attachTagsToFreeboard 시작: freeboardId={}, tagInputs={}", freeboardId, tagInputs);

        for (int i = 0; i < tagInputs.size(); i++) {
            String tagInput = tagInputs.get(i);
            log.info(">>> [{}/{}] 태그 처리 시작: '{}'", i+1, tagInputs.size(), tagInput);

            try {
                Tag tag = getOrCreateTag(tagInput.trim());
                log.info(">>> 태그 생성/조회 완료: tagId={}, tagName={}", tag.getTagId(), tag.getTagName());

                FreeboardTag freeboardTag = FreeboardTag.builder()
                        .freeboardId(freeboardId)
                        .tagId(tag.getTagId())
                        .tagDisplayName(tagInput.trim())
                        .build();

                log.info(">>> FreeboardTag insert 시도: {}", freeboardTag);

                int result = freeboardTagMapper.insert(freeboardTag);
                log.info(">>> insert 결과: result={}", result);

                if (result == 0) {
                    log.error(">>> insert 실패: result=0, freeboardTag={}", freeboardTag);
                    throw new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED);
                }

                log.info(">>> [{}/{}] 태그 처리 완료", i+1, tagInputs.size());
            } catch (CustomBusinessException e) {
                log.error(">>> [{}/{}] CustomBusinessException 발생: tagInput='{}', errorCode={}",
                        i+1, tagInputs.size(), tagInput, e.getErrorCode(), e);
                throw e;
            } catch (Exception e) {
                log.error(">>> [{}/{}] Exception 발생: tagInput='{}'",
                        i+1, tagInputs.size(), tagInput, e);
                throw new CustomBusinessException(TagErrorCode.TAG_SAVE_FAILED);
            }
        }

        log.info("자유게시판 태그 저장 완료: freeboardId={}, 태그 수={}", freeboardId, tagInputs.size());
    }

    public Map<Long, List<String>> getFreeboardTagsMap(List<Long> freeboardIds) {
        if (freeboardIds == null || freeboardIds.isEmpty()) {
            return new HashMap<>();
        }

        List<FreeboardTag> freeboardTags = freeboardTagMapper.findByFreeboardIdIn(freeboardIds);

        if (freeboardTags == null || freeboardTags.isEmpty()) {
            return new HashMap<>();
        }

        return freeboardTags.stream()
                .filter(tag -> tag != null && tag.getTagDisplayName() != null)
                .collect(Collectors.groupingBy(
                        FreeboardTag::getFreeboardId,
                        Collectors.mapping(FreeboardTag::getTagDisplayName, Collectors.toList())
                ));
    }

    public Map<Long, List<String>> getCodeboardTagsMap(List<Long> codeboardIds) {
        if (codeboardIds == null || codeboardIds.isEmpty()) {
            return new HashMap<>();
        }

        List<CodeboardTag> codeboardTags = codeboardTagMapper.findByCodeboardIdIn(codeboardIds);

        if (codeboardTags == null || codeboardTags.isEmpty()) {
            return new HashMap<>();
        }

        return codeboardTags.stream()
                .filter(tag -> tag != null && tag.getTagDisplayName() != null)
                .collect(Collectors.groupingBy(
                        CodeboardTag::getCodeboardId,
                        Collectors.mapping(CodeboardTag::getTagDisplayName, Collectors.toList())
                ));
    }

    public List<String> getCodeboardTags(Long codeboardId) {
        List<CodeboardTag> tags = codeboardTagMapper.findByCodeboardId(codeboardId);

        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        return tags.stream()
                .filter(tag -> tag != null && tag.getTagDisplayName() != null)
                .map(CodeboardTag::getTagDisplayName)
                .toList();
    }

    public List<String> getFreeboardTags(Long freeboardId) {
        log.info(">>> getFreeboardTags 호출: freeboardId={}", freeboardId);

        List<FreeboardTag> tags = freeboardTagMapper.findByFreeboardId(freeboardId);

        log.info(">>> Mapper 조회 결과 - null 여부: {}", tags == null);
        log.info(">>> Mapper 조회 결과 - 크기: {}", tags != null ? tags.size() : "null");
        log.info(">>> Mapper 조회 결과 - 내용: {}", tags);

        if (tags == null || tags.isEmpty()) {
            log.warn(">>> 조회된 태그가 없음: freeboardId={}", freeboardId);
            return Collections.emptyList();
        }

        for (FreeboardTag tag : tags) {
            log.info(">>> 개별 태그: freeboardId={}, tagId={}, displayName={}",
                    tag.getFreeboardId(), tag.getTagId(), tag.getTagDisplayName());
        }

        List<String> result = tags.stream()
                .filter(tag -> tag != null && tag.getTagDisplayName() != null)
                .map(FreeboardTag::getTagDisplayName)
                .toList();

        log.info(">>> 최종 반환할 태그 목록: {}", result);

        return result;
    }

    @Transactional
    public void updateCodeboardTags(Long codeboardId, List<String> tagInputs) {
        codeboardTagMapper.deleteByCodeboardId(codeboardId);

        if (tagInputs != null && !tagInputs.isEmpty()) {
            attachTagsToCodeboard(codeboardId, tagInputs);
        }
    }

    @Transactional
    public void updateFreeboardTags(Long freeboardId, List<String> tagInputs) {
        log.info(">>> updateFreeboardTags 시작: freeboardId={}, tagInputs={}", freeboardId, tagInputs);

        try {
            int deleteCount = freeboardTagMapper.deleteByFreeboardId(freeboardId);
            log.info(">>> 기존 태그 삭제 완료: freeboardId={}, 삭제된 개수={}", freeboardId, deleteCount);
        } catch (Exception e) {
            log.error(">>> 태그 삭제 실패: freeboardId={}", freeboardId, e);
            throw e;
        }

        if (tagInputs != null && !tagInputs.isEmpty()) {
            try {
                attachTagsToFreeboard(freeboardId, tagInputs);
                log.info(">>> 새 태그 저장 완료: freeboardId={}", freeboardId);
            } catch (Exception e) {
                log.error(">>> 태그 저장 실패: freeboardId={}", freeboardId, e);
                throw e;
            }
        }

        log.info(">>> updateFreeboardTags 완료: freeboardId={}", freeboardId);
    }

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
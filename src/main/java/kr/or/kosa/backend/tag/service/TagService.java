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
            return;
        }

        for (String tagInput : tagInputs) {
            Tag tag = getOrCreateTag(tagInput.trim());

            FreeboardTag freeboardTag = FreeboardTag.builder()
                    .freeboardId(freeboardId)
                    .tagId(tag.getTagId())
                    .tagDisplayName(tagInput.trim())
                    .build();

            int result = freeboardTagMapper.insert(freeboardTag);
            if (result == 0) {
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
        List<FreeboardTag> tags = freeboardTagMapper.findByFreeboardId(freeboardId);

        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        return tags.stream()
                .filter(tag -> tag != null && tag.getTagDisplayName() != null)
                .map(FreeboardTag::getTagDisplayName)
                .toList();
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
        freeboardTagMapper.deleteByFreeboardId(freeboardId);

        if (tagInputs != null && !tagInputs.isEmpty()) {
            attachTagsToFreeboard(freeboardId, tagInputs);
        }
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
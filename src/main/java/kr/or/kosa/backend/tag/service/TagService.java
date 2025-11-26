package kr.or.kosa.backend.tag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import kr.or.kosa.backend.tag.mapper.TagMapper;
import kr.or.kosa.backend.tag.dto.TagDto;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public List<kr.or.kosa.backend.tag.domain.Tag> getAllTags() {
        return tagMapper.findAllTags();
    }

    public List<kr.or.kosa.backend.tag.domain.Tag> getTagsByFreeboardId(Long id) {
        return tagMapper.findTagsByFreeboardId(id);
    }

    public List<kr.or.kosa.backend.tag.domain.Tag> getTagsByCodeboardId(Long id) {
        return tagMapper.findTagsByCodeboardId(id);
    }

    public void addTag(kr.or.kosa.backend.tag.domain.Tag tag) {
        tagMapper.insertTag(tag);
    }

    public void addFreeboardTag(TagDto dto) {
        tagMapper.insertFreeboardTag(dto);
    }

    public void addCodeboardTag(TagDto dto) {
        tagMapper.insertCodeboardTag(dto);
    }

    public void deleteByFreeboardId(Long id) {
        tagMapper.deleteByFreeboardId(id);
    }

    public void deleteByCodeboardId(Long id) {
        tagMapper.deleteByCodeboardId(id);
    }
}

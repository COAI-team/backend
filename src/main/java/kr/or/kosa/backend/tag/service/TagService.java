package kr.or.kosa.backend.tag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import kr.or.kosa.backend.tag.mapper.TagMapper;
import kr.or.kosa.backend.tag.domain.Tag;
import kr.or.kosa.backend.tag.dto.TagDTO;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public List<Tag> getAllTags() {
        return tagMapper.findAllTags();
    }

    public List<Tag> getTagsByFreeboardId(Long id) {
        return tagMapper.findTagsByFreeboardId(id);
    }

    public List<Tag> getTagsByCodeboardId(Long id) {
        return tagMapper.findTagsByCodeboardId(id);
    }

    public void addTag(Tag tag) {
        tagMapper.insertTag(tag);
    }

    public void addFreeboardTag(TagDTO dto) {
        tagMapper.insertFreeboardTag(dto);
    }

    public void addCodeboardTag(TagDTO dto) {
        tagMapper.insertCodeboardTag(dto);
    }

    public void deleteByFreeboardId(Long id) {
        tagMapper.deleteByFreeboardId(id);
    }

    public void deleteByCodeboardId(Long id) {
        tagMapper.deleteByCodeboardId(id);
    }
}

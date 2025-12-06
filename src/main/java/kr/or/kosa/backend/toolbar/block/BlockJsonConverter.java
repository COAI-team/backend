package kr.or.kosa.backend.toolbar.block;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 프론트에서 전달된 blocks(Object)를
 * 내부 도메인 타입(List<BlockShape>)으로 변환
 */
public final class BlockJsonConverter {

    private BlockJsonConverter() {
    }

    public static List<BlockShape> toBlockList(Object blocks, ObjectMapper objectMapper) {
        if (blocks == null) {
            throw new IllegalArgumentException("blocks is null");
        }

        List<BlockShapeImpl> implList = objectMapper.convertValue(
                blocks,
                new TypeReference<List<BlockShapeImpl>>() {}
        );

        return new ArrayList<>(implList);  // 업캐스팅된 새 리스트 반환
    }
}

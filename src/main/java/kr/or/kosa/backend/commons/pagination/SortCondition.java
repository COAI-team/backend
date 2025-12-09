package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;

// 정렬 조건 객체
// column 값은 반드시 enum을 통해 전달되어야 하며, 직접 문자열 주입 불가

@Getter
public class SortCondition {
    private final String column;
    private final SortDirection direction;

    // Enum 기반 생성자 (권장)
    public <T extends Enum<T>> SortCondition(T sortType, SortDirection direction) {
        validate(sortType, direction);
        this.column = extractColumnName(sortType);
        this.direction = direction;
    }

    private <T extends Enum<T>> void validate(T sortType, SortDirection direction) {
        if (sortType == null) {
            throw new IllegalArgumentException("정렬 타입은 필수입니다.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("정렬 방향은 필수입니다.");
        }
    }

    // Enum에서 getColumn() 메서드를 리플렉션으로 호출
    private <T extends Enum<T>> String extractColumnName(T sortType) {
        try {
            return (String) sortType.getClass()
                    .getMethod("getColumn")
                    .invoke(sortType);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "정렬 Enum은 반드시 getColumn() 메서드를 구현해야 합니다.", e
            );
        }
    }

    public String getDirectionSql() {
        return direction.name();
    }

    // MyBatis XML에서 ${sort.orderByClause} 사용을 위한 getter
    public String getOrderByClause() {
        return column + " " + direction.name();
    }
}
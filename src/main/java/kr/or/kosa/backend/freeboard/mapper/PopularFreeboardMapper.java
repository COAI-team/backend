package kr.or.kosa.backend.freeboard.mapper;

import kr.or.kosa.backend.freeboard.domain.PopularFreeboard;
import kr.or.kosa.backend.freeboard.dto.PopularFreeboardResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PopularFreeboardMapper {

    // 주간 인기글 조회
    List<PopularFreeboardResponseDto> findWeeklyPopularPosts(@Param("weekStartDate") LocalDate weekStartDate);

    // 주간 인기글 데이터 저장
    void insertWeeklyPopularPosts(@Param("posts") List<PopularFreeboard> posts);

    // 기존 주간 인기글 삭제 (배치 실행 전)
    void deleteWeeklyPopularPosts(@Param("weekStartDate") LocalDate weekStartDate);

    // 특정 기간 인기글 계산용 데이터 조회
    List<PopularFreeboard> calculateWeeklyPopularPosts(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit
    );
}

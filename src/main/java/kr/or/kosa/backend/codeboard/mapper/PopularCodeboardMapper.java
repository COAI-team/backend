package kr.or.kosa.backend.codeboard.mapper;

import kr.or.kosa.backend.codeboard.domain.PopularCodeboard;
import kr.or.kosa.backend.codeboard.dto.PopularCodeboardResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PopularCodeboardMapper {

    // 주간 인기글 조회
    List<PopularCodeboardResponseDto> findWeeklyPopularPosts(@Param("weekStartDate") LocalDate weekStartDate);

    // 주간 인기글 데이터 저장
    void insertWeeklyPopularPosts(@Param("posts") List<PopularCodeboard> posts);

    // 기존 주간 인기글 삭제
    void deleteWeeklyPopularPosts(@Param("weekStartDate") LocalDate weekStartDate);

    // 특정 기간 인기글 계산용 데이터 조회
    List<PopularCodeboard> calculateWeeklyPopularPosts(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit
    );
}
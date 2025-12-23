package kr.or.kosa.backend.freeboard.service;

import kr.or.kosa.backend.freeboard.domain.PopularFreeboard;
import kr.or.kosa.backend.freeboard.dto.PopularFreeboardResponseDto;
import kr.or.kosa.backend.freeboard.mapper.PopularFreeboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularFreeboardService {

    private final PopularFreeboardMapper popularFreeboardMapper;

    public List<PopularFreeboardResponseDto> getWeeklyPopularPosts() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        log.info("자유게시판 인기글 조회 - 기준 날짜: {}", thisWeekMonday);
        return popularFreeboardMapper.findWeeklyPopularPosts(thisWeekMonday);
    }

    public boolean isPopularPostsEmpty() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        List<PopularFreeboardResponseDto> posts = popularFreeboardMapper.findWeeklyPopularPosts(thisWeekMonday);
        return posts == null || posts.isEmpty();
    }

    @Transactional
    public void processWeeklyPopularPosts() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        LocalDate today = LocalDate.now();

        log.info("자유게시판 주간 인기글 배치 시작 - 기간: {} ~ {}", thisWeekMonday, today);

        popularFreeboardMapper.deleteWeeklyPopularPosts(thisWeekMonday);

        List<PopularFreeboard> calculatedPosts = popularFreeboardMapper.calculateWeeklyPopularPosts(
                thisWeekMonday,
                today,
                3
        );

        log.info("계산된 자유게시판 인기글 수: {}", calculatedPosts.size());

        if (calculatedPosts.isEmpty()) {
            log.warn("자유게시판 주간 인기글이 없습니다. 기간: {} ~ {}", thisWeekMonday, today);
            return;
        }

        List<PopularFreeboard> postsWithRanking = IntStream.range(0, calculatedPosts.size())
                .mapToObj(i -> {
                    PopularFreeboard post = calculatedPosts.get(i);
                    return PopularFreeboard.builder()
                            .freeboardId(post.getFreeboardId())
                            .weekStartDate(thisWeekMonday)
                            .weekEndDate(today)
                            .viewCount(post.getViewCount())
                            .likeCount(post.getLikeCount())
                            .commentCount(post.getCommentCount())
                            .popularityScore(post.getPopularityScore())
                            .ranking(i + 1)
                            .build();
                })
                .toList();

        popularFreeboardMapper.insertWeeklyPopularPosts(postsWithRanking);

        log.info("자유게시판 주간 인기글 배치 완료 - {} 건 저장", postsWithRanking.size());
    }

    private LocalDate getThisWeekMonday() {
        LocalDate today = LocalDate.now();
        return today.with(DayOfWeek.MONDAY);
    }

    private LocalDate getLastWeekMonday() {
        return getThisWeekMonday().minusWeeks(1);
    }
}
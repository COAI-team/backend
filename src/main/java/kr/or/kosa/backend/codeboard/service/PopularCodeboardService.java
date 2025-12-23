package kr.or.kosa.backend.codeboard.service;

import kr.or.kosa.backend.codeboard.domain.PopularCodeboard;
import kr.or.kosa.backend.codeboard.dto.PopularCodeboardResponseDto;
import kr.or.kosa.backend.codeboard.mapper.PopularCodeboardMapper;
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
public class PopularCodeboardService {

    private final PopularCodeboardMapper popularCodeboardMapper;

    public List<PopularCodeboardResponseDto> getWeeklyPopularPosts() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        log.info("코드게시판 인기글 조회 - 기준 날짜: {}", thisWeekMonday);
        return popularCodeboardMapper.findWeeklyPopularPosts(thisWeekMonday);
    }

    public boolean isPopularPostsEmpty() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        List<PopularCodeboardResponseDto> posts = popularCodeboardMapper.findWeeklyPopularPosts(thisWeekMonday);
        return posts == null || posts.isEmpty();
    }

    @Transactional
    public void processWeeklyPopularPosts() {
        LocalDate thisWeekMonday = getThisWeekMonday();
        LocalDate today = LocalDate.now();

        log.info("코드게시판 주간 인기글 배치 시작 - 기간: {} ~ {}", thisWeekMonday, today);

        popularCodeboardMapper.deleteWeeklyPopularPosts(thisWeekMonday);

        List<PopularCodeboard> calculatedPosts = popularCodeboardMapper.calculateWeeklyPopularPosts(
                thisWeekMonday,
                today,
                3
        );

        log.info("계산된 코드게시판 인기글 수: {}", calculatedPosts.size());

        if (calculatedPosts.isEmpty()) {
            log.warn("코드게시판 주간 인기글이 없습니다. 기간: {} ~ {}", thisWeekMonday, today);
            return;
        }

        List<PopularCodeboard> postsWithRanking = IntStream.range(0, calculatedPosts.size())
                .mapToObj(i -> {
                    PopularCodeboard post = calculatedPosts.get(i);
                    return PopularCodeboard.builder()
                            .codeboardId(post.getCodeboardId())
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

        popularCodeboardMapper.insertWeeklyPopularPosts(postsWithRanking);

        log.info("코드게시판 주간 인기글 배치 완료 - {} 건 저장", postsWithRanking.size());
    }

    private LocalDate getThisWeekMonday() {
        LocalDate today = LocalDate.now();
        return today.with(DayOfWeek.MONDAY);
    }

    private LocalDate getLastWeekMonday() {
        return getThisWeekMonday().minusWeeks(1);
    }
}
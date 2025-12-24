package kr.or.kosa.backend.freeboard.service;

import kr.or.kosa.backend.freeboard.domain.PopularFreeboard;
import kr.or.kosa.backend.freeboard.dto.PopularFreeboardResponseDto;
import kr.or.kosa.backend.freeboard.mapper.PopularFreeboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularFreeboardService {

    private final PopularFreeboardMapper popularFreeboardMapper;

    public List<PopularFreeboardResponseDto> getWeeklyPopularPosts() {
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        log.info("자유게시판 인기글 조회 - 기준 날짜: {}", weekAgo);
        return popularFreeboardMapper.findWeeklyPopularPosts(weekAgo);
    }

    public boolean isPopularPostsEmpty() {
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        List<PopularFreeboardResponseDto> posts = popularFreeboardMapper.findWeeklyPopularPosts(weekAgo);
        return posts == null || posts.isEmpty();
    }

    @Transactional
    public void processWeeklyPopularPosts() {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);

        log.info("자유게시판 주간 인기글 배치 시작 - 기간: {} ~ {}", weekAgo, today.minusDays(1));

        popularFreeboardMapper.deleteWeeklyPopularPosts(weekAgo);

        List<PopularFreeboard> calculatedPosts = popularFreeboardMapper.calculateWeeklyPopularPosts(
                weekAgo,
                today.minusDays(1),
                3
        );

        log.info("계산된 자유게시판 인기글 수: {}", calculatedPosts.size());

        if (calculatedPosts.isEmpty()) {
            log.warn("자유게시판 주간 인기글이 없습니다. 기간: {} ~ {}", weekAgo, today.minusDays(1));
            return;
        }

        List<PopularFreeboard> postsWithRanking = IntStream.range(0, calculatedPosts.size())
                .mapToObj(i -> {
                    PopularFreeboard post = calculatedPosts.get(i);
                    return PopularFreeboard.builder()
                            .freeboardId(post.getFreeboardId())
                            .weekStartDate(weekAgo)
                            .weekEndDate(today.minusDays(1))
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
}
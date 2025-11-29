package kr.or.kosa.backend.comment.service;

import kr.or.kosa.backend.codeboard.domain.Codeboard;
import kr.or.kosa.backend.codeboard.mapper.CodeboardMapper;
import kr.or.kosa.backend.comment.domain.Comment;
import kr.or.kosa.backend.comment.dto.CommentCreateRequest;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import kr.or.kosa.backend.comment.dto.CommentUpdateRequest;
import kr.or.kosa.backend.comment.dto.CommentWithRepliesResponse;
import kr.or.kosa.backend.comment.exception.CommentErrorCode;
import kr.or.kosa.backend.comment.mapper.CommentMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import kr.or.kosa.backend.like.service.LikeService;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.notification.service.NotificationService;
import kr.or.kosa.backend.notification.domain.NotificationType;
import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentMapper commentMapper;
    private final CodeboardMapper codeBoardMapper;
    private final FreeboardMapper freeboardMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final LikeService likeService;

    @Transactional
    public CommentResponse createComment(CommentCreateRequest request, Long userId) {
        // 게시글 존재 여부 확인
        Long boardAuthorId = getBoardAuthorId(request.boardType(), request.boardId());

        // 대댓글인 경우 부모 댓글 검증
        if (request.parentCommentId() != null) {
            Comment parentComment = commentMapper.selectCommentById(request.parentCommentId());
            if (parentComment == null) {
                throw new CustomBusinessException(CommentErrorCode.PARENT_NOT_FOUND);
            }

            // 대댓글의 대댓글 방지
            if (parentComment.getParentCommentId() != null) {
                throw new CustomBusinessException(CommentErrorCode.DEPTH_LIMIT_EXCEEDED);
            }

            // 부모 댓글 작성자에게 알림 발송
            if (!parentComment.getUserId().equals(userId)) {
                notificationService.sendNotification(
                        parentComment.getUserId(),
                        userId,
                        NotificationType.COMMENT_REPLY,
                        ReferenceType.COMMENT,
                        parentComment.getCommentId()
                );
            }
        }
        // 댓글인 경우 게시글 작성자에게 알림
        else {
            if (!boardAuthorId.equals(userId)) {
                ReferenceType referenceType = switch (request.boardType()) {
                    case "CODEBOARD" -> ReferenceType.POST_CODEBOARD;
                    case "FREEBOARD" -> ReferenceType.POST_FREEBOARD;
                    case "ALGORITHM" -> ReferenceType.POST_ALGORITHM;
                    default -> throw new CustomBusinessException(CommentErrorCode.INVALID_BOARD_TYPE);
                };

                notificationService.sendNotification(
                        boardAuthorId,
                        userId,
                        NotificationType.POST_COMMENT,
                        referenceType,
                        request.boardId()
                );
            }
        }

        Comment comment = Comment.builder()
                .boardId(request.boardId())
                .boardType(request.boardType())
                .parentCommentId(request.parentCommentId())
                .userId(userId)
                .content(request.content())
                .build();

        Long inserted = commentMapper.insertComment(comment);
        if (inserted == 0) {
            throw new CustomBusinessException(CommentErrorCode.INSERT_ERROR);
        }

        // DB에서 다시 조회하여 자동 생성된 필드 값 가져오기
        Comment savedComment = commentMapper.selectCommentById(comment.getCommentId());

        // 사용자 닉네임 조회
        User user = userMapper.findById(userId);
        String userNickname = user != null ? user.getNickname() : null;

        return CommentResponse.builder()
                .commentId(savedComment.getCommentId())
                .boardId(savedComment.getBoardId())
                .boardType(savedComment.getBoardType())
                .parentCommentId(savedComment.getParentCommentId())
                .userId(savedComment.getUserId())
                .userNickname(userNickname)
                .content(savedComment.getContent())
                .likeCount(savedComment.getLikeCount())
                .isLiked(false)
                .isAuthor(savedComment.getUserId().equals(boardAuthorId))
                .isDeleted(savedComment.getIsDeleted())
                .createdAt(savedComment.getCreatedAt())
                .updatedAt(savedComment.getUpdatedAt())
                .build();
    }

    public List<CommentWithRepliesResponse> getCommentsByBoard(Long boardId, String boardType, Long currentUserId) {
        // 1. 댓글만 조회
        List<Comment> comments = commentMapper.selectCommentsByBoard(boardId, boardType);

        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 모든 댓글 ID 수집
        List<Long> commentIds = comments.stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());

        // 3. 대댓글 한 번에 조회
        List<Comment> replies = commentMapper.selectRepliesByParentIds(commentIds);

        // 4. 댓글별로 대댓글 그룹핑
        Map<Long, List<Comment>> repliesByParentId = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentCommentId));

        // 5. 모든 사용자 ID 수집
        Set<Long> allUserIds = new HashSet<>();
        comments.forEach(c -> allUserIds.add(c.getUserId()));
        replies.forEach(r -> allUserIds.add(r.getUserId()));

        // 6. 사용자 정보 한 번에 조회
        Map<Long, String> userNicknameMap = getUserNicknameMap(new ArrayList<>(allUserIds));

        // 7. 현재 사용자가 좋아요 누른 댓글 ID 목록 조회
        List<Long> allCommentIds = new ArrayList<>(commentIds);
        allCommentIds.addAll(replies.stream().map(Comment::getCommentId).collect(Collectors.toList()));

        Set<Long> likedCommentIds = currentUserId != null
                ? new HashSet<>(likeService.getLikedIds(currentUserId, ReferenceType.COMMENT, allCommentIds))
                : Collections.emptySet();

        // 8. 게시글 작성자 ID 조회
        Long boardAuthorId = getBoardAuthorId(boardType, boardId);

        // 9. 응답 조립
        return comments.stream()
                .map(comment -> {
                    List<CommentResponse> replyResponses = repliesByParentId
                            .getOrDefault(comment.getCommentId(), Collections.emptyList())
                            .stream()
                            .map(reply -> CommentResponse.builder()
                                    .commentId(reply.getCommentId())
                                    .boardId(reply.getBoardId())
                                    .boardType(reply.getBoardType())
                                    .parentCommentId(reply.getParentCommentId())
                                    .userId(reply.getUserId())
                                    .userNickname(userNicknameMap.get(reply.getUserId()))
                                    .content(reply.getContent())
                                    .likeCount(reply.getLikeCount())
                                    .isLiked(likedCommentIds.contains(reply.getCommentId()))
                                    .isAuthor(reply.getUserId().equals(boardAuthorId))
                                    .isDeleted(reply.getIsDeleted())
                                    .createdAt(reply.getCreatedAt())
                                    .updatedAt(reply.getUpdatedAt())
                                    .build())
                            .collect(Collectors.toList());

                    return CommentWithRepliesResponse.builder()
                            .commentId(comment.getCommentId())
                            .boardId(comment.getBoardId())
                            .boardType(comment.getBoardType())
                            .userId(comment.getUserId())
                            .userNickname(userNicknameMap.get(comment.getUserId()))
                            .content(comment.getContent())
                            .likeCount(comment.getLikeCount())
                            .isLiked(likedCommentIds.contains(comment.getCommentId()))
                            .isAuthor(comment.getUserId().equals(boardAuthorId))
                            .isDeleted(comment.getIsDeleted())
                            .createdAt(comment.getCreatedAt())
                            .updatedAt(comment.getUpdatedAt())
                            .replies(replyResponses)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, CommentUpdateRequest request, Long userId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null) {
            throw new CustomBusinessException(CommentErrorCode.NOT_FOUND);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new CustomBusinessException(CommentErrorCode.NO_EDIT_PERMISSION);
        }

        if (comment.getIsDeleted()) {
            throw new CustomBusinessException(CommentErrorCode.ALREADY_DELETED);
        }

        comment.update(request.content());

        Long updated = commentMapper.updateComment(comment);
        if (updated == 0) {
            throw new CustomBusinessException(CommentErrorCode.UPDATE_ERROR);
        }

        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null) {
            throw new CustomBusinessException(CommentErrorCode.NOT_FOUND);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new CustomBusinessException(CommentErrorCode.NO_DELETE_PERMISSION);
        }

        // 소프트 딜리트
        Long deleted = commentMapper.deleteComment(commentId);
        if (deleted == 0) {
            throw new CustomBusinessException(CommentErrorCode.DELETE_ERROR);
        }

        // 관련 좋아요 삭제
        likeService.deleteByReference(ReferenceType.COMMENT, commentId);

        // 관련 알림 삭제
        notificationService.deleteByReference(ReferenceType.COMMENT, commentId);
    }

    private Long getBoardAuthorId(String boardType, Long boardId) {
        return switch (boardType) {
            case "CODEBOARD" -> {
                Codeboard codeBoard = codeBoardMapper.selectById(boardId);
                if (codeBoard == null) {
                    throw new CustomBusinessException(CommentErrorCode.BOARD_NOT_FOUND);
                }
                yield codeBoard.getUserId();
            }
            case "FREEBOARD" -> {
                Freeboard freeBoard = freeboardMapper.selectById(boardId);
                if (freeBoard == null) {
                    throw new CustomBusinessException(CommentErrorCode.BOARD_NOT_FOUND);
                }
                yield freeBoard.getUserId();
            }
            default -> throw new CustomBusinessException(CommentErrorCode.INVALID_BOARD_TYPE);
        };
    }

    private Map<Long, String> getUserNicknameMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<User> users = userMapper.selectUsersByIds(userIds);
        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getNickname
                ));
    }
}
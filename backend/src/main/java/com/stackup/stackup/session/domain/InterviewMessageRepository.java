package com.stackup.stackup.session.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {

    List<InterviewMessage> findBySession_IdOrderBySequenceNumberAsc(Long sessionId);

    Optional<InterviewMessage> findFirstBySession_IdOrderBySequenceNumberDesc(Long sessionId);

    long countBySession_Id(Long sessionId);

    boolean existsBySession_IdAndRole(Long sessionId, MessageRole role);

    Optional<InterviewMessage> findBySession_IdAndIdempotencyKey(Long sessionId, String idempotencyKey);

    @Query("select coalesce(max(m.sequenceNumber), 0) from InterviewMessage m where m.session.id = :sessionId")
    int findMaxSequenceBySessionId(@Param("sessionId") Long sessionId);

    // 오답노트 목록. 삭제된 세션의 질문은 제외한다(세션을 지우면 그 질문도 사라지는 게 맞다).
    @Query("""
        select m from InterviewMessage m
        where m.session.user.id = :userId
          and m.session.deleted = false
          and m.bookmarked = true
        order by m.id desc
        """)
    List<InterviewMessage> findBookmarkedByOwner(@Param("userId") Long userId);

    // STT 콜백을 기다리다 멈춰 있는 음성 답변. content 가 아직 pending sentinel 이고
    // 진행 중 세션에 속한 것만. (callback.voice 가 유실되면 이 상태로 영구히 남는다.)
    @Query("""
        select m from InterviewMessage m
        where m.role = com.stackup.stackup.session.domain.MessageRole.INTERVIEWEE
          and m.status = com.stackup.stackup.session.domain.MessageStatus.CREATED
          and m.content = :pendingText
          and m.createdAt < :before
          and m.session.deleted = false
          and m.session.status = com.stackup.stackup.session.domain.SessionStatus.IN_PROGRESS
        """)
    List<InterviewMessage> findStaleTranscribing(@Param("pendingText") String pendingText,
                                                 @Param("before") java.time.Instant before);

    // 질문에 달린 답변(있으면 1개). 오답노트에 '내 답변 + 코칭'을 함께 보여주기 위해.
    List<InterviewMessage> findByParentMessage_IdIn(List<Long> parentMessageIds);
}

package com.stackup.stackup.session.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionTimeoutSweeperTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionTimeoutService timeoutService;
    @InjectMocks SessionTimeoutSweeper sweeper;

    @Test
    void sweep_endsOnlyTimedOutSessions() {
        // maxDurationMinutes=30. started 2시간 전 → 초과. started 방금 → 미초과.
        InterviewSession timedOut = sessionStartedMinutesAgo(60L, 120);
        InterviewSession fresh = sessionStartedMinutesAgo(61L, 1);
        when(sessionRepository.findByStatusAndDeletedFalse(SessionStatus.IN_PROGRESS))
            .thenReturn(List.of(timedOut, fresh));

        sweeper.sweep();

        verify(timeoutService).endTimedOut(60L);
        verify(timeoutService, never()).endTimedOut(61L);
    }

    private InterviewSession sessionStartedMinutesAgo(Long id, int minutesAgo) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30, null, null);
        ReflectionTestUtils.setField(s, "id", id);
        s.start();
        ReflectionTestUtils.setField(s, "startedAt", Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        return s;
    }
}

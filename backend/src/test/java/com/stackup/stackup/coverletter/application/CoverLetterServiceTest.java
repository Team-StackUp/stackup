package com.stackup.stackup.coverletter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.coverletter.application.dto.CoverLetterCreateCommand;
import com.stackup.stackup.coverletter.application.dto.CoverLetterItem;
import com.stackup.stackup.coverletter.application.dto.CoverLetterResult;
import com.stackup.stackup.coverletter.application.event.CoverLetterUploadedEvent;
import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.coverletter.domain.CoverLetterRepository;
import com.stackup.stackup.coverletter.domain.CoverLetterStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CoverLetterServiceTest {

    @Mock CoverLetterRepository coverLetterRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks CoverLetterService service;

    @Test
    void create_persistsSerializesItemsAndPublishesEvent() {
        User user = user();
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(coverLetterRepository.save(any(CoverLetter.class)))
            .thenAnswer(inv -> {
                CoverLetter cl = inv.getArgument(0);
                ReflectionTestUtils.setField(cl, "id", 7L);
                return cl;
            });

        CoverLetterCreateCommand command = new CoverLetterCreateCommand(
            "OO기업 공채",
            List.of(
                new CoverLetterItem("지원동기", "저는 ..."),
                new CoverLetterItem("성장과정", "어릴 때 ..."),
                new CoverLetterItem("빈 문항", "   ")  // 답변 공백 → 제거됨
            )
        );

        CoverLetterResult result = service.create(1L, command);

        assertThat(result.status()).isEqualTo(CoverLetterStatus.PENDING);
        assertThat(result.items()).hasSize(2);  // 빈 답변 문항 제외
        assertThat(result.items().get(0).question()).isEqualTo("지원동기");
        verify(events).publishEvent(any(CoverLetterUploadedEvent.class));
    }

    @Test
    void create_rejectsWhenNoAnswerableItem() {
        CoverLetterCreateCommand command = new CoverLetterCreateCommand(
            "빈 자소서",
            List.of(new CoverLetterItem("문항", "  "))
        );

        assertThatThrownBy(() -> service.create(1L, command))
            .isInstanceOf(DomainException.class);
    }

    private User user() {
        User u = User.createGithubUser(123L, "octocat", null, null, "tok");
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}

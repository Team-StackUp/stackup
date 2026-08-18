package com.stackup.stackup.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.application.event.WebResumeRegisteredEvent;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.resume.domain.ResumeStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

// 웹 이력서(URL) 등록 — US-09. S3 업로드 없이 URL 만 저장하고 analyze.web 트리거 이벤트를 낸다.
@ExtendWith(MockitoExtension.class)
class WebResumeRegisterTest {

    @Mock ResumeRepository resumeRepository;
    @Mock UserRepository userRepository;
    @Mock ObjectStorageClient storage;
    @Mock ApplicationEventPublisher events;

    // DNS 는 fake — 단위 테스트가 네트워크에 의존하지 않게 한다.
    private static final WebResumeUrlValidator VALIDATOR = new WebResumeUrlValidator(host -> {
        if (host.endsWith("example.com")) {
            return new java.net.InetAddress[] {java.net.InetAddress.getByName("93.184.216.34")};
        }
        return new java.net.InetAddress[] {java.net.InetAddress.getByName(host)};
    });

    private ResumeService service() {
        return new ResumeService(resumeRepository, userRepository, storage, VALIDATOR, events);
    }

    @Test
    void registerWeb_savesUrlWithoutStorageAndPublishesEvent() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user()));
        when(resumeRepository.existsByUser_IdAndSourceUrlAndDeletedFalse(1L, "https://example.com/portfolio"))
            .thenReturn(false);
        when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> {
            Resume r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 42L);
            return r;
        });

        ResumeResult result = service().registerWeb(1L, "  https://example.com/portfolio  ");

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.fileType()).isEqualTo(ResumeFileType.WEB);
        assertThat(result.sourceUrl()).isEqualTo("https://example.com/portfolio");
        assertThat(result.filePath()).isNull();
        assertThat(result.fileSize()).isNull();
        assertThat(result.status()).isEqualTo(ResumeStatus.PENDING);
        // 목록 표시용 이름은 host + path 로 만든다(original_filename 이 NOT NULL).
        assertThat(result.originalFilename()).isEqualTo("example.com/portfolio");

        // URL 은 AI 가 직접 fetch 하므로 Core 는 S3 에 아무것도 올리지 않는다.
        verify(storage, never()).put(any(), any(), anyLong(), any());

        ArgumentCaptor<WebResumeRegisteredEvent> captor =
            ArgumentCaptor.forClass(WebResumeRegisteredEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().resumeId()).isEqualTo(42L);
    }

    @Test
    void registerWeb_usesHostOnlyWhenPathIsEmpty() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user()));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeResult result = service().registerWeb(1L, "https://blog.example.com/");

        assertThat(result.originalFilename()).isEqualTo("blog.example.com");
    }

    // 같은 URL 을 두 번 등록하면 임베딩이 중복돼 질문이 한쪽으로 쏠린다.
    @Test
    void registerWeb_rejectsDuplicateUrl() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user()));
        when(resumeRepository.existsByUser_IdAndSourceUrlAndDeletedFalse(1L, "https://example.com/me"))
            .thenReturn(true);

        assertThatThrownBy(() -> service().registerWeb(1L, "https://example.com/me"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.RESUME_URL_DUPLICATE));

        verify(resumeRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    // SSRF 차단은 사용자 조회보다 먼저 — 내부망 주소는 DB 를 건드리지도 않는다.
    @Test
    void registerWeb_rejectsInternalAddressBeforeTouchingDb() {
        assertThatThrownBy(() -> service().registerWeb(1L, "http://127.0.0.1:8080/api/internal/documents/1"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.RESUME_INVALID_URL));

        verify(userRepository, never()).findByIdAndDeletedFalse(anyLong());
        verify(resumeRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void registerWeb_rejectsWhenUserNotFound() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registerWeb(1L, "https://example.com/me"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.USER_NOT_FOUND));
    }

    private User user() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}

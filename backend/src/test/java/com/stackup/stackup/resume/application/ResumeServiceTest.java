package com.stackup.stackup.resume.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.application.dto.ResumeUploadCommand;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

class ResumeServiceTest {

    private ResumeRepository resumeRepository;
    private UserRepository userRepository;
    private AnalyzedDocumentRepository documentRepository;
    private ObjectStorageClient storage;
    private ApplicationEventPublisher events;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        resumeRepository = Mockito.mock(ResumeRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        documentRepository = Mockito.mock(AnalyzedDocumentRepository.class);
        storage = Mockito.mock(ObjectStorageClient.class);
        events = Mockito.mock(ApplicationEventPublisher.class);
        service = new ResumeService(resumeRepository, userRepository, documentRepository, storage, events);
    }

    @Test
    void upload_rejects_empty_file() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(1L, new ResumeUploadCommand(empty)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_EMPTY_FILE);

        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    void upload_rejects_wrong_content_type() {
        MockMultipartFile png = new MockMultipartFile("file", "x.pdf", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.upload(1L, new ResumeUploadCommand(png)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_INVALID_FILE_TYPE);
    }

    @Test
    void upload_rejects_non_pdf_extension() {
        MockMultipartFile doc = new MockMultipartFile("file", "x.doc", "application/pdf", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.upload(1L, new ResumeUploadCommand(doc)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_INVALID_FILE_TYPE);
    }

    @Test
    void upload_rejects_over_20mb_file() {
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        big[0]='%'; big[1]='P'; big[2]='D'; big[3]='F'; big[4]='-';
        MockMultipartFile huge = new MockMultipartFile("file", "x.pdf", "application/pdf", big);
        assertThatThrownBy(() -> service.upload(1L, new ResumeUploadCommand(huge)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_FILE_TOO_LARGE);
    }

    @Test
    void upload_rejects_file_without_pdf_magic_bytes() {
        byte[] bogus = "not a pdf at all".getBytes();
        MockMultipartFile fake = new MockMultipartFile("file", "x.pdf", "application/pdf", bogus);
        assertThatThrownBy(() -> service.upload(1L, new ResumeUploadCommand(fake)))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_INVALID_FILE_TYPE);
    }

    @Test
    void upload_persists_resume_and_publishes_event() {
        byte[] pdf = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'};
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "resume.pdf", "application/pdf", pdf
        );

        com.stackup.stackup.user.domain.User user = Mockito.mock(com.stackup.stackup.user.domain.User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.getReferenceById(42L)).thenReturn(user);

        when(resumeRepository.save(any())).thenAnswer(inv -> {
            com.stackup.stackup.resume.domain.Resume r = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 7L);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "createdAt", java.time.Instant.now());
            org.springframework.test.util.ReflectionTestUtils.setField(r, "updatedAt", java.time.Instant.now());
            return r;
        });

        com.stackup.stackup.resume.application.dto.ResumeResult result =
            service.upload(42L, new ResumeUploadCommand(file));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo(com.stackup.stackup.resume.domain.ResumeStatus.PENDING);

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).put(keyCaptor.capture(), any(), eq((long) pdf.length), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).startsWith("resumes/raw/42/").endsWith(".pdf");

        org.mockito.ArgumentCaptor<com.stackup.stackup.resume.application.event.ResumeUploadedEvent> eventCaptor =
            org.mockito.ArgumentCaptor.forClass(com.stackup.stackup.resume.application.event.ResumeUploadedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().resumeId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(42L);
    }

    @Test
    void list_returns_page_of_user_resumes() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        com.stackup.stackup.resume.domain.Resume r = com.stackup.stackup.resume.domain.Resume.create(
            Mockito.mock(com.stackup.stackup.user.domain.User.class),
            "r.pdf", "k", 1L, com.stackup.stackup.resume.domain.ResumeFileType.PDF
        );
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        var page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(r), pageable, 1);

        when(resumeRepository.findByUser_IdAndDeletedFalse(eq(42L), eq(pageable))).thenReturn(page);

        var result = service.list(42L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    void get_throws_when_not_found_or_other_user() {
        when(resumeRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(42L, 1L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_NOT_FOUND);
    }

    @Test
    void get_returns_resume_when_owner() {
        com.stackup.stackup.resume.domain.Resume r = com.stackup.stackup.resume.domain.Resume.create(
            Mockito.mock(com.stackup.stackup.user.domain.User.class),
            "r.pdf", "k", 1L, com.stackup.stackup.resume.domain.ResumeFileType.PDF
        );
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        when(resumeRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.of(r));

        var result = service.get(42L, 1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void delete_throws_resume_not_found_for_unknown() {
        when(resumeRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.delete(42L, 1L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_NOT_FOUND);
    }

    @Test
    void delete_throws_resume_in_use_when_active_session_references_it() {
        com.stackup.stackup.resume.domain.Resume r = com.stackup.stackup.resume.domain.Resume.create(
            Mockito.mock(com.stackup.stackup.user.domain.User.class),
            "r.pdf", "k", 1L, com.stackup.stackup.resume.domain.ResumeFileType.PDF
        );
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        when(resumeRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.of(r));
        when(documentRepository.existsActiveSessionContextForResume(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(42L, 1L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.RESUME_IN_USE);
    }

    @Test
    void delete_marks_resume_deleted_when_no_active_session() {
        com.stackup.stackup.resume.domain.Resume r = com.stackup.stackup.resume.domain.Resume.create(
            Mockito.mock(com.stackup.stackup.user.domain.User.class),
            "r.pdf", "k", 1L, com.stackup.stackup.resume.domain.ResumeFileType.PDF
        );
        org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 1L);
        when(resumeRepository.findByIdAndUser_IdAndDeletedFalse(1L, 42L)).thenReturn(java.util.Optional.of(r));
        when(documentRepository.existsActiveSessionContextForResume(1L)).thenReturn(false);

        service.delete(42L, 1L);

        assertThat(r.isDeleted()).isTrue();
    }
}

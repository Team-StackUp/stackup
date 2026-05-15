package com.stackup.stackup.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;

class ResumeTest {

    @Test
    void create_initializes_pending_resume() {
        User user = mock(User.class);
        Resume resume = Resume.create(
            user,
            "ada-resume.pdf",
            "resumes/raw/1/uuid.pdf",
            1_048_576L,
            ResumeFileType.PDF
        );

        assertThat(resume.getOriginalFilename()).isEqualTo("ada-resume.pdf");
        assertThat(resume.getFilePath()).isEqualTo("resumes/raw/1/uuid.pdf");
        assertThat(resume.getFileSize()).isEqualTo(1_048_576L);
        assertThat(resume.getFileType()).isEqualTo(ResumeFileType.PDF);
        assertThat(resume.getStatus()).isEqualTo(ResumeStatus.PENDING);
        assertThat(resume.isDeleted()).isFalse();
    }

    @Test
    void softDelete_sets_deleted_true() {
        User user = mock(User.class);
        Resume resume = Resume.create(user, "f.pdf", "k", 1L, ResumeFileType.PDF);

        resume.softDelete();

        assertThat(resume.isDeleted()).isTrue();
    }

    private static User mock(Class<User> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}

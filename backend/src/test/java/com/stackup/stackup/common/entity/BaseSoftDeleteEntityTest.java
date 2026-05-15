package com.stackup.stackup.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class BaseSoftDeleteEntityTest {

    @Entity
    static class Dummy extends BaseSoftDeleteEntity {
    }

    @Test
    void markDeleted_sets_deleted_true() {
        Dummy dummy = new Dummy();
        assertThat(dummy.isDeleted()).isFalse();

        dummy.markDeleted();

        assertThat(dummy.isDeleted()).isTrue();
    }
}

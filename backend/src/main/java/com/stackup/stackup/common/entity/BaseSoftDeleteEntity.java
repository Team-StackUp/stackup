package com.stackup.stackup.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseTimeAndUpdateEntity {

    @Column(name = "is_deleted", nullable = false)
    protected boolean deleted = false;
}

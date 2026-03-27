package com.stackup.stackup.user.domain.consent;

import com.stackup.stackup.common.entity.BaseTimeEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_consents",
        indexes = {
                @Index(name = "idx_user_consents_user", columnList = "user_id"),
                @Index(name = "idx_user_consents_type", columnList = "user_id, consent_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "consent_type", nullable = false, length = 50)
    private String consentType;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Column(name = "is_agreed", nullable = false)
    private boolean agreed = true;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}

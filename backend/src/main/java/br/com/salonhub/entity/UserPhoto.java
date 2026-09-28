package br.com.salonhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPhoto {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserPhoto(Long userId, String contentType, byte[] data, OffsetDateTime updatedAt) {
        this.userId = userId;
        replace(contentType, data, updatedAt);
    }

    public void replace(String contentType, byte[] data, OffsetDateTime updatedAt) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = updatedAt;
    }
}

CREATE TABLE establishment_gallery_photos (
    id BIGSERIAL PRIMARY KEY,
    establishment_id BIGINT NOT NULL REFERENCES establishments (id) ON DELETE CASCADE,
    media_id BIGINT NOT NULL UNIQUE REFERENCES media (id),
    display_order INTEGER NOT NULL CHECK (display_order >= 0)
);

ALTER TABLE media ADD CONSTRAINT uk_media_id_establishment UNIQUE (id, establishment_id);
ALTER TABLE establishment_gallery_photos
    ADD CONSTRAINT fk_gallery_media_tenant FOREIGN KEY (media_id, establishment_id)
    REFERENCES media (id, establishment_id);

CREATE INDEX idx_establishment_gallery_order ON establishment_gallery_photos (establishment_id, display_order, id);

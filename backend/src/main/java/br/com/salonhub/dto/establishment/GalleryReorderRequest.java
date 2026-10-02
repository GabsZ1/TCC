package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GalleryReorderRequest(@NotNull List<@NotNull Long> photoIds) {}

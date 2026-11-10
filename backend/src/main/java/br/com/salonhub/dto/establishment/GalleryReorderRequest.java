package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Define os dados recebidos pela API no fluxo de estabelecimentos e suas validações de entrada. */
public record GalleryReorderRequest(@NotNull List<@NotNull Long> photoIds) {}

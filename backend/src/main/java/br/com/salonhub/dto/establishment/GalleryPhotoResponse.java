package br.com.salonhub.dto.establishment;

/** Define os dados devolvidos pela API no fluxo de estabelecimentos, sem expor as entidades persistidas. */
public record GalleryPhotoResponse(Long id, int position) {}

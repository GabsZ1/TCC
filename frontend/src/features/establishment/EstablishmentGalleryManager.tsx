/** Gerencia a seleção, a ordem e a exclusão de fotos da galeria. */
import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, GripVertical, Images, Plus, Trash2 } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { GalleryPhotoImage } from './GalleryPhotoImage'
import { deleteGalleryPhoto, fetchGallery, GALLERY_LIMIT, reorderGallery, uploadGalleryPhoto } from './establishmentGalleryApi'

/** Controla lote de envio, ordem das fotos e exclusão com atualização da galeria. */
export function EstablishmentGalleryManager({ establishmentId }: { establishmentId: number }) {
  const queryClient = useQueryClient()
  const queryKey = ['establishment-gallery', establishmentId, true]
  const gallery = useQuery({ queryKey, queryFn: () => fetchGallery(establishmentId, true) })
  const photos = gallery.data ?? []
  const fileInput = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploadedCount, setUploadedCount] = useState(0)
  const [draggedId, setDraggedId] = useState<number | null>(null)
  const [photoToRemove, setPhotoToRemove] = useState<number | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['establishment-gallery', establishmentId] })
  // Envia os arquivos em sequência para exibir progresso e respeitar a ordem de seleção.
  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const [index, file] of files.entries()) {
        await uploadGalleryPhoto(file)
        setUploadedCount(index + 1)
      }
    },
    onSuccess: () => setError(null),
    onError: (failure) => setError(getApiErrorMessage(failure, 'Não foi possível enviar as fotos.')),
    onSettled: refresh,
  })
  const remove = useMutation({
    mutationFn: deleteGalleryPhoto,
    onSuccess: () => { setPhotoToRemove(null); setError(null); refresh() },
    onError: (failure) => { setPhotoToRemove(null); setError(getApiErrorMessage(failure, 'Não foi possível remover a foto.')) },
  })
  const reorder = useMutation({
    mutationFn: reorderGallery,
    onSuccess: () => { setError(null); refresh() },
    onError: (failure) => setError(getApiErrorMessage(failure, 'Não foi possível alterar a ordem.')),
  })
  const busy = upload.isPending || remove.isPending || reorder.isPending

  /** Rejeita o lote inteiro quando excede a capacidade ou contém uma imagem inválida. */
  function selectFiles(files: FileList | null) {
    if (!files?.length) return
    const selected = Array.from(files)
    const remaining = GALLERY_LIMIT - photos.length
    if (selected.length > remaining) return setError(`Você pode adicionar mais ${remaining} foto(s). O limite é ${GALLERY_LIMIT}.`)
    if (selected.some((file) => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024))
      return setError('Use imagens JPG, PNG ou WEBP de até 2 MB cada.')
    setError(null)
    setUploadedCount(0)
    upload.mutate(selected)
  }

  /** Envia a sequência completa de IDs após arrastar ou usar os botões de posição. */
  function move(from: number, to: number) {
    if (busy || from < 0 || to < 0 || to >= photos.length || from === to) return
    const ids = photos.map((photo) => photo.id)
    ids.splice(to, 0, ids.splice(from, 1)[0])
    reorder.mutate(ids)
  }

  return (
    <section className="surface-card mt-7 p-5 sm:p-6" aria-label="Galeria de mídia">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold text-text-950"><Images size={20} className="text-primary-600" /> Galeria de mídia</h2>
          <p className="mt-1 text-sm text-text-600">Mostre o espaço aos clientes. Arraste as fotos no computador ou use as setas para alterar a ordem. As mudanças são salvas imediatamente.</p>
          <p className="mt-1 text-xs font-medium text-text-500">{photos.length} de {GALLERY_LIMIT} fotos · JPG, PNG ou WEBP · até 2 MB cada</p>
        </div>
        {gallery.isSuccess && photos.length < GALLERY_LIMIT && <>
          <button type="button" disabled={busy} onClick={() => fileInput.current?.click()} className="primary-button w-full shrink-0 justify-center disabled:opacity-50 sm:w-auto"><Plus size={17} /> {upload.isPending ? `Enviando ${uploadedCount}/${upload.variables?.length ?? 0}` : 'Adicionar fotos'}</button>
          <input ref={fileInput} type="file" multiple accept="image/jpeg,image/png,image/webp" disabled={busy} className="hidden" onChange={(event) => { selectFiles(event.target.files); event.target.value = '' }} />
        </>}
      </div>

      {error && <p role="alert" className="mt-4 rounded-xl bg-danger-50 px-4 py-3 text-sm text-danger-600">{error}</p>}
      {gallery.isLoading && <p className="mt-5 text-sm text-text-500">Carregando galeria...</p>}
      {gallery.isError && <div className="mt-5 flex flex-wrap items-center gap-3"><p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(gallery.error, 'Não foi possível carregar a galeria.')}</p><button type="button" onClick={() => gallery.refetch()} className="secondary-button">Tentar novamente</button></div>}
      {!gallery.isLoading && !gallery.isError && photos.length === 0 && <div className="mt-5 rounded-2xl border border-dashed border-background-300 bg-background-100/50 px-5 py-10 text-center text-sm text-text-600">A galeria está vazia. Adicione fotos do ambiente, da fachada ou dos seus trabalhos.</div>}

      {photos.length > 0 && <ol className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {photos.map((photo, index) => <li key={photo.id} draggable={!busy} onDragStart={() => setDraggedId(photo.id)} onDragEnd={() => setDraggedId(null)} onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); if (draggedId !== null) move(photos.findIndex((item) => item.id === draggedId), index); setDraggedId(null) }} className={`overflow-hidden rounded-2xl border bg-white ${draggedId === photo.id ? 'border-primary-500 opacity-60' : 'border-background-300'}`}>
          <div className="relative">
            <GalleryPhotoImage establishmentId={establishmentId} photoId={photo.id} mine lazy alt={`Foto ${index + 1} da galeria`} className="aspect-[4/3] w-full object-cover" />
            <span className="absolute left-3 top-3 rounded-lg bg-text-950/75 px-2 py-1 text-xs font-semibold text-white">{index + 1} / {photos.length}</span>
          </div>
          <div className="flex items-center gap-1 p-2">
            <GripVertical size={16} className="mr-auto hidden text-text-400 sm:block" aria-hidden="true" />
            <button type="button" disabled={busy || index === 0} onClick={() => move(index, index - 1)} className="grid size-10 place-items-center rounded-lg text-text-600 hover:bg-background-100 disabled:opacity-30" aria-label={`Mover foto ${index + 1} para antes`} title={`Mover foto ${index + 1} para antes`}><ArrowLeft size={17} /></button>
            <button type="button" disabled={busy || index === photos.length - 1} onClick={() => move(index, index + 1)} className="grid size-10 place-items-center rounded-lg text-text-600 hover:bg-background-100 disabled:opacity-30" aria-label={`Mover foto ${index + 1} para depois`} title={`Mover foto ${index + 1} para depois`}><ArrowRight size={17} /></button>
            <button type="button" disabled={busy} onClick={() => setPhotoToRemove(photo.id)} className="ml-auto grid size-10 place-items-center rounded-lg text-danger-600 hover:bg-danger-50 disabled:opacity-30" aria-label={`Remover foto ${index + 1}`} title={`Remover foto ${index + 1} da galeria`}><Trash2 size={17} /></button>
          </div>
        </li>)}
      </ol>}
      {photoToRemove !== null && <ConfirmDialog title="Remover foto" message="Remover esta foto da galeria?" confirmLabel="Remover foto" isLoading={remove.isPending} onConfirm={() => remove.mutate(photoToRemove)} onCancel={() => setPhotoToRemove(null)} />}
    </section>
  )
}

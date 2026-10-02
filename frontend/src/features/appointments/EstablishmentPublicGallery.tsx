import { useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Images } from 'lucide-react'
import { GalleryPhotoImage } from '@/features/establishment/GalleryPhotoImage'
import { fetchGallery } from '@/features/establishment/establishmentGalleryApi'

export function EstablishmentPublicGallery({ establishmentId, establishmentName }: { establishmentId: number; establishmentName: string }) {
  const gallery = useQuery({
    queryKey: ['establishment-gallery', establishmentId, false],
    queryFn: () => fetchGallery(establishmentId),
    staleTime: 5 * 60 * 1000,
  })
  const photos = gallery.data ?? []
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const touchStart = useRef<{ x: number; y: number } | null>(null)
  if (gallery.isError) return <div role="alert" className="flex flex-wrap items-center gap-2 rounded-xl bg-background-100 px-4 py-3 text-sm text-text-600">Não foi possível carregar as fotos. <button type="button" onClick={() => gallery.refetch()} className="font-semibold text-primary-700 underline">Tentar novamente</button></div>
  if (photos.length === 0) return null

  const selectedIndex = Math.max(0, photos.findIndex((photo) => photo.id === selectedId))
  const selected = photos[selectedIndex]
  const goTo = (index: number) => setSelectedId(photos[(index + photos.length) % photos.length].id)

  return (
    <section aria-label={`Galeria de fotos de ${establishmentName}`}>
      <div className="mb-3 flex items-end justify-between gap-3">
        <h3 className="flex items-center gap-2 text-lg font-semibold text-text-950"><Images size={18} className="text-primary-600" /> Nosso espaço</h3>
        <span className="text-xs font-medium text-text-500" aria-live="polite">{selectedIndex + 1} de {photos.length}</span>
      </div>
      <div className="relative overflow-hidden rounded-2xl bg-background-100 touch-pan-y" onTouchStart={(event) => { touchStart.current = { x: event.touches[0].clientX, y: event.touches[0].clientY } }} onTouchCancel={() => { touchStart.current = null }} onTouchEnd={(event) => {
        if (!touchStart.current || photos.length < 2) return
        const deltaX = event.changedTouches[0].clientX - touchStart.current.x
        const deltaY = event.changedTouches[0].clientY - touchStart.current.y
        if (Math.abs(deltaX) > 50 && Math.abs(deltaX) > Math.abs(deltaY) * 1.3) goTo(selectedIndex + (deltaX < 0 ? 1 : -1))
        touchStart.current = null
      }}>
        <GalleryPhotoImage establishmentId={establishmentId} photoId={selected.id} alt={`${establishmentName}: foto ${selectedIndex + 1} de ${photos.length}`} className="aspect-[4/3] w-full object-contain sm:aspect-[16/9] sm:max-h-[30rem]" />
        {photos.length > 1 && <>
          <button type="button" onClick={() => goTo(selectedIndex - 1)} className="absolute left-2 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-white/90 text-text-900 shadow-md transition hover:bg-white focus-visible:outline-2 focus-visible:outline-primary-600 sm:left-4" aria-label="Foto anterior"><ChevronLeft size={23} /></button>
          <button type="button" onClick={() => goTo(selectedIndex + 1)} className="absolute right-2 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-white/90 text-text-900 shadow-md transition hover:bg-white focus-visible:outline-2 focus-visible:outline-primary-600 sm:right-4" aria-label="Próxima foto"><ChevronRight size={23} /></button>
        </>}
      </div>
      {photos.length > 1 && <div className="mt-3 flex snap-x gap-2 overflow-x-auto pb-2" aria-label="Escolher foto da galeria">
        {photos.map((photo, index) => <button key={photo.id} type="button" onClick={() => setSelectedId(photo.id)} aria-label={`Mostrar foto ${index + 1}`} aria-current={index === selectedIndex ? 'true' : undefined} className={`w-20 shrink-0 snap-start overflow-hidden rounded-xl border-2 transition sm:w-24 ${index === selectedIndex ? 'border-primary-600' : 'border-transparent opacity-75 hover:opacity-100'}`}>
          <GalleryPhotoImage establishmentId={establishmentId} photoId={photo.id} lazy alt="" className="aspect-[4/3] w-full object-cover" />
        </button>)}
      </div>}
      {photos.length > 1 && <p className="mt-1 text-xs text-text-500 sm:hidden">Deslize a foto ou toque nas miniaturas para navegar.</p>}
    </section>
  )
}

/** Renderiza uma foto da galeria com o tratamento visual de carregamento e falha. */
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ImageIcon } from 'lucide-react'
import { fetchGalleryImage } from './establishmentGalleryApi'

/** Carrega uma foto da galeria e lida com a ausência da imagem. */
export function GalleryPhotoImage({ establishmentId, photoId, mine = false, lazy = false, alt, className }: {
  establishmentId: number
  photoId: number
  mine?: boolean
  lazy?: boolean
  alt: string
  className: string
}) {
  const holder = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(!lazy || typeof IntersectionObserver === 'undefined')
  useEffect(() => {
    if (!lazy || visible) return
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) { setVisible(true); observer.disconnect() }
    }, { rootMargin: '200px' })
    if (holder.current) observer.observe(holder.current)
    return () => observer.disconnect()
  }, [lazy, visible])
  const image = useQuery({
    queryKey: ['gallery-image', establishmentId, photoId, mine],
    queryFn: () => fetchGalleryImage(establishmentId, photoId, mine),
    staleTime: 10 * 60 * 1000,
    retry: false,
    enabled: visible,
  })
  if (image.data) return <img src={image.data} alt={alt} className={className} />
  return <div ref={holder} role="img" aria-label={image.isError ? 'Foto indisponível' : 'Carregando foto'} className={`${className} grid place-items-center bg-background-100 text-text-400`}><ImageIcon size={24} /></div>
}

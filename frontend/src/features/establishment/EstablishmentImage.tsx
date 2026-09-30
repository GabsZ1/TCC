import { useQuery } from '@tanstack/react-query'
import { fetchEstablishmentImage, type ImageKind } from './establishmentImagesApi'

export function EstablishmentImage({ establishmentId, kind, hasImage, mine = false, className = '' }: {
  establishmentId: number; kind: ImageKind; hasImage: boolean; mine?: boolean; className?: string
}) {
  const image = useQuery({ queryKey: ['establishment-image', establishmentId, kind, mine],
    queryFn: () => fetchEstablishmentImage(establishmentId, kind, mine), enabled: hasImage, retry: false, staleTime: 5 * 60_000 })
  if (image.data) return <img src={image.data} alt={kind === 'logo' ? 'Logo do estabelecimento' : 'Capa do estabelecimento'} className={className} />
  return <div aria-label={kind === 'logo' ? 'Sem logo' : 'Sem imagem de capa'} className={`${className} grid place-items-center bg-primary-100 text-sm font-semibold text-primary-700`}>{kind === 'logo' ? 'ME' : 'Bem-vindo'}</div>
}

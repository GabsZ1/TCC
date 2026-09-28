import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchEmployeePhoto } from '@/features/employees/services/employeesApi'
import { fetchCatalogProfessionalPhoto } from '@/features/appointments/appointmentsApi'

interface Props {
  id: number
  name: string
  hasPhoto: boolean
  size?: 'sm' | 'md' | 'lg'
  /** Quando informado, busca a foto pelo catálogo do estabelecimento (visão do cliente). */
  catalogEstablishmentId?: number
}

/** Foto do funcionário (carregada com o token) ou, na falta dela, as iniciais. */
export function EmployeeAvatar({ id, name, hasPhoto, size = 'md', catalogEstablishmentId }: Props) {
  const photo = useQuery({
    queryKey: catalogEstablishmentId ? ['catalog-professional-photo', catalogEstablishmentId, id] : ['employee-photo', id],
    queryFn: () => (catalogEstablishmentId ? fetchCatalogProfessionalPhoto(catalogEstablishmentId, id) : fetchEmployeePhoto(id)),
    enabled: hasPhoto,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
  const url = useMemo(() => (hasPhoto && photo.data ? URL.createObjectURL(photo.data) : null), [hasPhoto, photo.data])
  useEffect(() => () => { if (url) URL.revokeObjectURL(url) }, [url])

  const dimension = size === 'lg' ? 'size-20 rounded-2xl text-lg' : size === 'sm' ? 'size-6 rounded-lg text-[0.6rem]' : 'size-9 rounded-xl text-xs'
  if (url) {
    return <img src={url} alt={`Foto de ${name}`} className={`${dimension} shrink-0 object-cover`} />
  }
  const initials = name.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('')
  return <span className={`grid ${dimension} shrink-0 place-items-center bg-secondary-100 font-bold text-secondary-700`}>{initials}</span>
}

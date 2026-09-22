import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AtSign, MapPin, Phone, Save, Store } from 'lucide-react'
import { getApiErrorMessage } from '@/lib/apiError'
import {
  fetchMyEstablishmentProfile,
  updateMyEstablishmentProfile,
  type EstablishmentProfile,
  type EstablishmentProfileInput,
} from '@/features/establishment/establishmentApi'

const QUERY_KEY = ['my-establishment-profile']

export function EstablishmentProfilePage() {
  const queryClient = useQueryClient()
  const profileQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyEstablishmentProfile })
  const [form, setForm] = useState<EstablishmentProfileInput | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const profile = profileQuery.data
  const currentForm = form ?? (profile ? profileToForm(profile) : null)

  const mutation = useMutation({
    mutationFn: updateMyEstablishmentProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      setForm(profileToForm(updated))
      setMessage('Perfil público atualizado com sucesso.')
      setErrorMessage(null)
    },
    onError: (error) => {
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível atualizar o perfil público.'))
    },
  })

  function updateField(field: keyof EstablishmentProfileInput, value: string) {
    if (currentForm) setForm({ ...currentForm, [field]: value })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!currentForm) return
    setMessage(null)
    setErrorMessage(null)
    mutation.mutate({
      ...currentForm,
      postalCode: currentForm.postalCode?.replace(/\D/g, '').slice(0, 8) || null,
      state: currentForm.state?.trim().toUpperCase() || null,
    })
  }

  if (profileQuery.isLoading) return <PageContainer><StateCard>Carregando perfil público...</StateCard></PageContainer>
  if (profileQuery.isError || !currentForm) return <PageContainer><StateCard error>{getApiErrorMessage(profileQuery.error, 'Não foi possível carregar o perfil público.')}</StateCard></PageContainer>

  return (
    <PageContainer>
      <header>
        <p className="eyebrow">Presença pública</p>
        <h1 className="page-title mt-2">Perfil do estabelecimento</h1>
        <p className="page-subtitle">Defina as informações que os clientes verão ao consultar os detalhes do seu estabelecimento.</p>
      </header>

      <form onSubmit={submit} className="surface-card mt-7 overflow-hidden">
        <div className="border-b border-background-300 bg-background-100/60 p-5 sm:p-6">
          <SectionHeader icon={<Store size={20} />} title="Informações públicas" description="Serviços, profissionais e horários ativos são incluídos automaticamente no perfil." />
        </div>
        <fieldset disabled={mutation.isPending} className="space-y-7 p-5 sm:p-6">
          {message && <p role="status" className="rounded-xl bg-success-50 px-4 py-3 text-sm text-success-600">{message}</p>}
          {errorMessage && <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}

          <div>
            <div className="flex items-center gap-2 text-text-700"><Store size={18} /><h2 className="font-semibold text-text-900">Apresentação</h2></div>
            <label className="mt-4 block">
              <span className="text-sm font-medium text-text-800">Quem somos</span>
              <textarea value={currentForm.aboutUs ?? ''} maxLength={3000} rows={6} onChange={(event) => updateField('aboutUs', event.target.value)} placeholder="Conte a história, os diferenciais e o estilo do seu estabelecimento..." className="mt-2 w-full resize-y rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" />
              <span className="mt-1 block text-right text-xs text-text-500">{currentForm.aboutUs?.length ?? 0}/3000</span>
            </label>
          </div>

          <div className="border-t border-background-300 pt-6">
            <div className="flex items-center gap-2 text-text-700"><MapPin size={18} /><h2 className="font-semibold text-text-900">Endereço</h2></div>
            <p className="mt-1 text-sm text-text-600">Os campos estão separados para facilitar a futura integração com o mapa.</p>
            <div className="mt-4 grid gap-5 sm:grid-cols-6">
              <ProfileField label="CEP" value={currentForm.postalCode} onChange={(value) => updateField('postalCode', value.replace(/\D/g, '').slice(0, 8))} placeholder="00000000" className="sm:col-span-2" />
              <ProfileField label="Logradouro" value={currentForm.street} onChange={(value) => updateField('street', value)} placeholder="Rua, avenida..." className="sm:col-span-4" />
              <ProfileField label="Número" value={currentForm.streetNumber} onChange={(value) => updateField('streetNumber', value)} className="sm:col-span-2" />
              <ProfileField label="Complemento" value={currentForm.addressComplement} onChange={(value) => updateField('addressComplement', value)} className="sm:col-span-4" />
              <ProfileField label="Bairro" value={currentForm.neighborhood} onChange={(value) => updateField('neighborhood', value)} className="sm:col-span-2" />
              <ProfileField label="Cidade" value={currentForm.city} onChange={(value) => updateField('city', value)} className="sm:col-span-3" />
              <ProfileField label="UF" value={currentForm.state} onChange={(value) => updateField('state', value.toUpperCase().slice(0, 2))} placeholder="SP" className="sm:col-span-1" />
            </div>
          </div>

          <div className="border-t border-background-300 pt-6">
            <div className="flex items-center gap-2 text-text-700"><Phone size={18} /><h2 className="font-semibold text-text-900">Contato público</h2></div>
            <div className="mt-4 grid gap-5 sm:grid-cols-3">
              <ProfileField label="WhatsApp" value={currentForm.whatsapp} onChange={(value) => updateField('whatsapp', value)} placeholder="(00) 00000-0000" />
              <ProfileField label="Telefone" value={currentForm.phone} onChange={(value) => updateField('phone', value)} placeholder="(00) 0000-0000" />
              <ProfileField label="Instagram" value={currentForm.instagram} onChange={(value) => updateField('instagram', value)} placeholder="@seuperfil" icon={<AtSign size={15} />} />
            </div>
          </div>

          <div className="rounded-xl bg-secondary-50 px-4 py-3 text-sm leading-6 text-text-700">A futura galeria de fotos poderá ser ligada diretamente a este perfil.</div>
        </fieldset>
        <div className="flex justify-end border-t border-background-300 p-4 sm:px-6">
          <button type="submit" disabled={mutation.isPending} className="primary-button disabled:cursor-not-allowed disabled:opacity-60"><Save size={18} /> {mutation.isPending ? 'Salvando...' : 'Salvar perfil público'}</button>
        </div>
      </form>
    </PageContainer>
  )
}

function ProfileField({ label, value, onChange, placeholder, className = '', icon }: { label: string; value: string | null; onChange: (value: string) => void; placeholder?: string; className?: string; icon?: React.ReactNode }) {
  return <label className={`block ${className}`}><span className="flex items-center gap-1.5 text-sm font-medium text-text-800">{icon}{label}</span><input value={value ?? ''} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="mt-2 w-full rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" /></label>
}
function SectionHeader({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) { return <div><div className="flex items-center gap-2 text-text-600">{icon}<h2 className="text-lg font-semibold text-text-900">{title}</h2></div><p className="mt-1 text-sm text-text-600">{description}</p></div> }
function PageContainer({ children }: { children: React.ReactNode }) { return <div className="page-shell">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function profileToForm(profile: EstablishmentProfile): EstablishmentProfileInput { return { aboutUs: profile.aboutUs ?? '', instagram: profile.instagram ?? '', whatsapp: profile.whatsapp ?? '', phone: profile.phone ?? '', postalCode: profile.postalCode ?? '', street: profile.street ?? '', streetNumber: profile.streetNumber ?? '', addressComplement: profile.addressComplement ?? '', neighborhood: profile.neighborhood ?? '', city: profile.city ?? '', state: profile.state ?? '' } }

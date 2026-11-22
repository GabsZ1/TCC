/** Edita as informações públicas apresentadas aos clientes. */
import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { IMaskInput } from 'react-imask'
import { AtSign, Images, MapPin, Phone, Save, Store } from 'lucide-react'
import { getApiErrorMessage } from '@/lib/apiError'
import { CepNotFoundError, cepDigits, formatCep, lookupCep } from '@/lib/cep'
import { PHONE_MASK, formatPhone } from '@/lib/phone'
import { EstablishmentImage } from './EstablishmentImage'
import { EstablishmentGalleryManager } from './EstablishmentGalleryManager'
import { EstablishmentSectionHeader } from './EstablishmentSectionHeader'
import { deleteEstablishmentImage, uploadEstablishmentImage, type ImageKind } from './establishmentImagesApi'
import {
  fetchMyEstablishmentProfile,
  updateMyEstablishmentProfile,
  type EstablishmentProfile,
  type EstablishmentProfileInput,
} from '@/features/establishment/establishmentApi'

const QUERY_KEY = ['my-establishment-profile']

/** Gerencia capa ou logotipo com prévia e ações de troca e remoção. */
function ProfileImageControl({ profile, kind }: { profile: EstablishmentProfile; kind: ImageKind }) {
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const hasImage = kind === 'logo' ? profile.hasLogo : profile.hasCover
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: QUERY_KEY })
    queryClient.invalidateQueries({ queryKey: ['establishment-image', profile.establishmentId, kind] })
  }
  const upload = useMutation({ mutationFn: (file: File) => uploadEstablishmentImage(kind, file), onSuccess: refresh })
  const remove = useMutation({ mutationFn: () => deleteEstablishmentImage(kind), onSuccess: refresh })
  const busy = upload.isPending || remove.isPending
  function selectImage(file: File | undefined) {
    if (!file) return
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setValidationError('Use uma imagem JPG, PNG ou WEBP de até 2 MB.')
      return
    }
    setValidationError(null)
    remove.reset()
    upload.mutate(file)
  }
  return <div className="space-y-3"><h3 className="text-sm font-semibold">{kind === 'logo' ? 'Logo' : 'Capa'}</h3>
    <EstablishmentImage establishmentId={profile.establishmentId} kind={kind} mine hasImage={hasImage} className={kind === 'logo' ? 'size-28 rounded-xl object-cover' : 'h-28 w-full rounded-xl object-cover'} />
    <div className="flex flex-wrap gap-2">
      <button type="button" disabled={busy} onClick={() => fileInput.current?.click()} className="secondary-button min-h-11 justify-center disabled:opacity-50">{upload.isPending ? 'Enviando...' : hasImage ? 'Trocar imagem' : 'Adicionar imagem'}</button>
      {hasImage && <button type="button" disabled={busy} onClick={() => { setValidationError(null); upload.reset(); remove.mutate() }} className="min-h-11 rounded-xl px-3 text-sm font-semibold text-danger-600 hover:bg-danger-50 disabled:opacity-50">{remove.isPending ? 'Removendo...' : 'Remover'}</button>}
    </div>
    <input ref={fileInput} aria-label={`Enviar ${kind}`} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} className="hidden" onChange={event => { selectImage(event.target.files?.[0]); event.target.value = '' }} />
    {validationError && <p role="alert" className="text-sm text-danger-600">{validationError}</p>}
    {(upload.isError || remove.isError) && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(upload.error ?? remove.error, 'Não foi possível atualizar a imagem.')}</p>}
  </div>
}

/** Edita os dados públicos e sincroniza endereço, coordenadas e imagens do perfil. */
export function EstablishmentProfilePage() {
  const queryClient = useQueryClient()
  const profileQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyEstablishmentProfile })
  const [form, setForm] = useState<EstablishmentProfileInput | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [locationWarning, setLocationWarning] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [cepStatus, setCepStatus] = useState<{ tone: 'info' | 'error'; text: string } | null>(null)
  const cepRequest = useRef<AbortController | null>(null)
  const streetNumberInput = useRef<HTMLInputElement>(null)
  const profile = profileQuery.data
  const currentForm = form ?? (profile ? profileToForm(profile) : null)

  const mutation = useMutation({
    mutationFn: updateMyEstablishmentProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      setForm(profileToForm(updated))
      setMessage(updated.locationAvailable
        ? 'Perfil atualizado. Localização obtida pelo endereço ou CEP e salva para a busca por proximidade.'
        : updated.street && updated.streetNumber && updated.city && updated.state
          ? 'Perfil atualizado. Não foi possível localizar este endereço ou CEP; confira os dados e salve novamente.'
          : 'Perfil atualizado. Preencha CEP, rua, número, cidade e UF para calcular a localização.')
      setLocationWarning(!updated.locationAvailable)
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

  /** Ao completar os 8 dígitos do CEP, busca o endereço e preenche logradouro, bairro, cidade e UF. */
  function changePostalCode(value: string) {
    if (!currentForm) return
    const digits = cepDigits(value)
    setForm({ ...currentForm, postalCode: digits })
    cepRequest.current?.abort()
    setCepStatus(null)
    if (digits.length !== 8) return

    const controller = new AbortController()
    cepRequest.current = controller
    setCepStatus({ tone: 'info', text: 'Buscando endereço...' })
    lookupCep(digits, controller.signal)
      .then((address) => {
        if (controller.signal.aborted) return
        // Campo que o CEP não informa (ex.: CEP único da cidade) mantém o que já estava digitado.
        setForm((previous) => {
          const base = previous ?? currentForm
          return {
            ...base,
            street: address.street || base.street,
            neighborhood: address.neighborhood || base.neighborhood,
            city: address.city || base.city,
            state: address.state || base.state,
          }
        })
        setCepStatus(address.street ? null : { tone: 'info', text: 'CEP geral da cidade: preencha o logradouro e o bairro.' })
        streetNumberInput.current?.focus()
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setCepStatus(error instanceof CepNotFoundError
          ? { tone: 'error', text: 'CEP não encontrado. Confira o número ou preencha o endereço manualmente.' }
          : { tone: 'error', text: 'Não foi possível consultar o CEP agora. Preencha o endereço manualmente.' })
      })
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
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Presença pública</p>
        <h1 className="page-title mt-2">Perfil do estabelecimento</h1>
        <p className="page-subtitle">Defina as informações que os clientes verão ao consultar os detalhes do seu estabelecimento.</p>
      </header>

      {profile && <section className="surface-card mt-7 p-5 sm:p-6"><EstablishmentSectionHeader icon={<Images size={20} />} title="Logo e capa" description="Escolha imagens JPG, PNG ou WEBP de até 2 MB cada. As mudanças são salvas imediatamente." /><div className="mt-5 grid gap-5 sm:grid-cols-2">
        <ProfileImageControl profile={profile} kind="logo" />
        <ProfileImageControl profile={profile} kind="capa" />
      </div></section>}

      {profile && <EstablishmentGalleryManager establishmentId={profile.establishmentId} />}

      <form onSubmit={submit} className="mt-7 space-y-5">
        <fieldset disabled={mutation.isPending} className="space-y-5">
          <section className="surface-card p-5 sm:p-6">
            <EstablishmentSectionHeader icon={<Store size={20} />} title="Apresentação" description="Conte sobre o espaço e seus diferenciais. Serviços, equipe e horários ativos entram automaticamente no perfil público." />
            <label className="mt-4 block">
              <span className="text-sm font-medium text-text-800">Quem somos</span>
              <textarea value={currentForm.aboutUs ?? ''} maxLength={3000} rows={6} onChange={(event) => updateField('aboutUs', event.target.value)} placeholder="Conte a história, os diferenciais e o estilo do seu estabelecimento..." className="mt-2 w-full resize-y rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" />
              <span className="mt-1 block text-right text-xs text-text-500">{currentForm.aboutUs?.length ?? 0}/3000</span>
            </label>
          </section>

          <section className="surface-card p-5 sm:p-6">
            <EstablishmentSectionHeader icon={<MapPin size={20} />} title="Endereço" description="Informe o endereço exibido no perfil público e usado na busca por proximidade." />
            <div className="mt-4 grid gap-5 sm:grid-cols-6">
              <ProfileField label="CEP" value={formatCep(currentForm.postalCode)} onChange={changePostalCode} maxLength={9} placeholder="00000-000" inputMode="numeric" className="sm:col-span-2" hint={cepStatus?.text ?? 'Digite o CEP para preencher o endereço.'} hintTone={cepStatus?.tone} />
              <ProfileField label="Logradouro" value={currentForm.street} onChange={(value) => updateField('street', value)} maxLength={150} placeholder="Rua, avenida..." className="sm:col-span-4" />
              <ProfileField label="Número" value={currentForm.streetNumber} onChange={(value) => updateField('streetNumber', value)} maxLength={20} className="sm:col-span-2" inputRef={streetNumberInput} />
              <ProfileField label="Complemento" value={currentForm.addressComplement} onChange={(value) => updateField('addressComplement', value)} maxLength={100} className="sm:col-span-4" />
              <ProfileField label="Bairro" value={currentForm.neighborhood} onChange={(value) => updateField('neighborhood', value)} maxLength={100} className="sm:col-span-2" />
              <ProfileField label="Cidade" value={currentForm.city} onChange={(value) => updateField('city', value)} maxLength={100} className="sm:col-span-3" />
              <ProfileField label="UF" value={currentForm.state} onChange={(value) => updateField('state', value.toUpperCase().slice(0, 2))} maxLength={2} pattern="AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO" placeholder="SP" className="sm:col-span-1" />
            </div>
          </section>

          <section className="surface-card p-5 sm:p-6">
            <EstablishmentSectionHeader icon={<Phone size={20} />} title="Contato público" description="Canais que os clientes podem usar para falar com o estabelecimento." />
            <div className="mt-4 grid gap-5 sm:grid-cols-3">
              <PhoneField label="WhatsApp" value={currentForm.whatsapp} onChange={(value) => updateField('whatsapp', value)} />
              <PhoneField label="Telefone" value={currentForm.phone} onChange={(value) => updateField('phone', value)} />
              <ProfileField label="Instagram" value={currentForm.instagram} onChange={(value) => updateField('instagram', value)} maxLength={100} placeholder="@seuperfil" icon={<AtSign size={15} />} />
            </div>
          </section>
        </fieldset>
        {message && <p role="status" className={`rounded-xl px-4 py-3 text-sm ${locationWarning ? 'bg-warning-50 text-warning-700' : 'bg-success-50 text-success-700'}`}>{message}</p>}
        {errorMessage && <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
        <div className="surface-card flex justify-end p-4">
          <button type="submit" disabled={mutation.isPending} className="primary-button w-full justify-center disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"><Save size={18} /> {mutation.isPending ? 'Salvando...' : 'Salvar alterações'}</button>
        </div>
      </form>
    </PageContainer>
  )
}

interface ProfileFieldProps {
  label: string
  value: string | null
  onChange: (value: string) => void
  placeholder?: string
  className?: string
  icon?: React.ReactNode
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode']
  inputRef?: React.Ref<HTMLInputElement>
  maxLength?: number
  pattern?: string
  hint?: string
  hintTone?: 'info' | 'error'
}
function ProfileField({ label, value, onChange, placeholder, className = '', icon, inputMode, inputRef, maxLength, pattern, hint, hintTone = 'info' }: ProfileFieldProps) {
  return <label className={`block ${className}`}><span className="flex items-center gap-1.5 text-sm font-medium text-text-800">{icon}{label}</span><input ref={inputRef} value={value ?? ''} inputMode={inputMode} maxLength={maxLength} pattern={pattern} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="mt-2 w-full rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" />{hint && <span role={hintTone === 'error' ? 'alert' : 'status'} className={`mt-1.5 block text-xs ${hintTone === 'error' ? 'text-danger-600' : 'text-text-500'}`}>{hint}</span>}</label>
}
function PhoneField({ label, value, onChange }: { label: string; value: string | null; onChange: (value: string) => void }) {
  return <label className="block"><span className="text-sm font-medium text-text-800">{label}</span><IMaskInput mask={PHONE_MASK} value={formatPhone(value)} onAccept={(next: string) => onChange(next)} inputMode="tel" maxLength={20} placeholder="(00) 00000-0000" className="mt-2 w-full rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" /></label>
}
function PageContainer({ children }: { children: React.ReactNode }) { return <div className="page-shell">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function profileToForm(profile: EstablishmentProfile): EstablishmentProfileInput { return { aboutUs: profile.aboutUs ?? '', instagram: profile.instagram ?? '', whatsapp: profile.whatsapp ?? '', phone: profile.phone ?? '', postalCode: profile.postalCode ?? '', street: profile.street ?? '', streetNumber: profile.streetNumber ?? '', addressComplement: profile.addressComplement ?? '', neighborhood: profile.neighborhood ?? '', city: profile.city ?? '', state: profile.state ?? '' } }

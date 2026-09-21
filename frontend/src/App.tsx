import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { AppLayout } from '@/components/layout/AppLayout'

const LoginPage = lazy(() => import('@/features/auth/pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const ForbiddenPage = lazy(() => import('@/features/auth/pages/ForbiddenPage').then((module) => ({ default: module.ForbiddenPage })))
const DashboardPage = lazy(() => import('@/features/dashboard/pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const EmployeesListPage = lazy(() => import('@/features/employees/pages/EmployeesListPage').then((module) => ({ default: module.EmployeesListPage })))
const InitialRegistrationPage = lazy(() => import('@/features/auth/pages/InitialRegistrationPage').then((module) => ({ default: module.InitialRegistrationPage })))
const ClientRegistrationPage = lazy(() => import('@/features/auth/pages/ClientRegistrationPage').then((module) => ({ default: module.ClientRegistrationPage })))
const FirstAccessPage = lazy(() => import('@/features/auth/pages/FirstAccessPage').then((module) => ({ default: module.FirstAccessPage })))
const ServicesPage = lazy(() => import('@/features/services/ServicesPage').then((module) => ({ default: module.ServicesPage })))
const AppointmentsPage = lazy(() => import('@/features/appointments/AppointmentsPage').then((module) => ({ default: module.AppointmentsPage })))
const BookingPage = lazy(() => import('@/features/appointments/BookingPage').then((module) => ({ default: module.BookingPage })))
const ProfessionalAgendaPage = lazy(() => import('@/features/appointments/ProfessionalAgendaPage').then((module) => ({ default: module.ProfessionalAgendaPage })))
const MyServicesPage = lazy(() => import('@/features/services/MyServicesPage').then((module) => ({ default: module.MyServicesPage })))
const EstablishmentPage = lazy(() => import('@/features/establishment/EstablishmentPage').then((module) => ({ default: module.EstablishmentPage })))
const AdminAgendaPage = lazy(() => import('@/features/appointments/AdminAgendaPage').then((module) => ({ default: module.AdminAgendaPage })))
const CommissionReportPage = lazy(() => import('@/features/commissions/CommissionReportPage').then((module) => ({ default: module.CommissionReportPage })))

function App() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cadastro" element={<InitialRegistrationPage />} />
      <Route path="/cadastro-cliente" element={<ClientRegistrationPage />} />
      <Route path="/primeiro-acesso" element={<FirstAccessPage />} />
      <Route path="/sem-permissao" element={<ForbiddenPage />} />

      {/* Rotas autenticadas, dentro do layout com sidebar */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />

          {/* Exemplo de restrição por perfil: apenas admin do estabelecimento */}
          <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
            <Route path="/funcionarios" element={<EmployeesListPage />} />
            <Route path="/servicos" element={<ServicesPage />} />
            <Route path="/estabelecimento" element={<EstablishmentPage />} />
            <Route path="/agenda" element={<AdminAgendaPage />} />
            <Route path="/relatorio-comissoes" element={<CommissionReportPage />} />
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['CLIENT']} />}>
            <Route path="/agendamentos" element={<AppointmentsPage />} />
            <Route path="/estabelecimentos/:establishmentId/agendar" element={<BookingPage />} />
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['PROFESSIONAL']} />}>
            <Route path="/minha-agenda" element={<ProfessionalAgendaPage />} />
            <Route path="/meus-servicos" element={<MyServicesPage />} />
          </Route>
        </Route>
      </Route>
      </Routes>
    </Suspense>
  )
}

function RouteLoading() {
  return <div className="grid min-h-screen place-items-center bg-sand-50 text-sm font-medium text-brand-600">Carregando página...</div>
}

export default App

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
const MyCompletedServicesPage = lazy(() => import('@/features/appointments/MyCompletedServicesPage').then((module) => ({ default: module.MyCompletedServicesPage })))
const MyServicesPage = lazy(() => import('@/features/services/MyServicesPage').then((module) => ({ default: module.MyServicesPage })))
const EstablishmentPage = lazy(() => import('@/features/establishment/EstablishmentPage').then((module) => ({ default: module.EstablishmentPage })))
const EstablishmentProfilePage = lazy(() => import('@/features/establishment/EstablishmentProfilePage').then((module) => ({ default: module.EstablishmentProfilePage })))
const AdminAgendaPage = lazy(() => import('@/features/appointments/AdminAgendaPage').then((module) => ({ default: module.AdminAgendaPage })))
const CommissionReportPage = lazy(() => import('@/features/commissions/CommissionReportPage').then((module) => ({ default: module.CommissionReportPage })))
const EstablishmentAppointmentsPage = lazy(() => import('@/features/reports/EstablishmentAppointmentsPage').then((module) => ({ default: module.EstablishmentAppointmentsPage })))
const ForgotPasswordPage = lazy(() => import('@/features/auth/pages/ForgotPasswordPage').then((module) => ({ default: module.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('@/features/auth/pages/ResetPasswordPage').then((module) => ({ default: module.ResetPasswordPage })))
const MyProfilePage = lazy(() => import('@/features/client/MyProfilePage').then((module) => ({ default: module.MyProfilePage })))
const ChangePasswordPage = lazy(() => import('@/features/auth/pages/ChangePasswordPage').then((module) => ({ default: module.ChangePasswordPage })))
const SystemEstablishmentsPage = lazy(() => import('@/features/system-admin/SystemEstablishmentsPage').then((module) => ({ default: module.SystemEstablishmentsPage })))

function App() {
  return (
    <Suspense fallback={<RouteLoading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/cadastro" element={<InitialRegistrationPage />} />
        <Route path="/cadastro-cliente" element={<ClientRegistrationPage />} />
        <Route path="/primeiro-acesso" element={<FirstAccessPage />} />
        <Route path="/recuperar-senha" element={<ForgotPasswordPage />} />
        <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
        <Route path="/sem-permissao" element={<ForbiddenPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/alterar-senha" element={<ChangePasswordPage />} />

            <Route element={<ProtectedRoute allowedRoles={['SYSTEM_ADMIN']} />}>
              <Route path="/admin/estabelecimentos" element={<SystemEstablishmentsPage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
              <Route path="/funcionarios" element={<EmployeesListPage />} />
              <Route path="/servicos" element={<ServicesPage />} />
              <Route path="/estabelecimento" element={<EstablishmentPage />} />
              <Route path="/perfil-estabelecimento" element={<EstablishmentProfilePage />} />
              <Route path="/agenda" element={<AdminAgendaPage />} />
              <Route path="/relatorio-comissoes" element={<CommissionReportPage />} />
              <Route path="/relatorio-atendimentos" element={<EstablishmentAppointmentsPage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['CLIENT']} />}>
              <Route path="/agendamentos" element={<AppointmentsPage />} />
              <Route path="/estabelecimentos/:establishmentId/agendar" element={<BookingPage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['CLIENT', 'PROFESSIONAL']} />}>
              <Route path="/meu-perfil" element={<MyProfilePage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['PROFESSIONAL']} />}>
              <Route path="/minha-agenda" element={<ProfessionalAgendaPage />} />
              <Route path="/meus-servicos" element={<MyServicesPage />} />
              <Route path="/meus-atendimentos" element={<MyCompletedServicesPage />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </Suspense>
  )
}

function RouteLoading() {
  return (
    <div className="grid min-h-screen place-items-center bg-background-100">
      <div className="flex items-center gap-3 text-sm font-bold text-text-600">
        <span className="size-2 animate-pulse rounded-full bg-primary-600" />
        Carregando SalonHub
      </div>
    </div>
  )
}

export default App

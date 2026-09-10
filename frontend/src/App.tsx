import { Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { AppLayout } from '@/components/layout/AppLayout'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { ForbiddenPage } from '@/features/auth/pages/ForbiddenPage'
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { EmployeesListPage } from '@/features/employees/pages/EmployeesListPage'
import { InitialRegistrationPage } from '@/features/auth/pages/InitialRegistrationPage'
import { ClientRegistrationPage } from '@/features/auth/pages/ClientRegistrationPage'
import { ServicesPage } from '@/features/services/ServicesPage'
import { AppointmentsPage } from '@/features/appointments/AppointmentsPage'
import { BookingPage } from '@/features/appointments/BookingPage'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cadastro" element={<InitialRegistrationPage />} />
      <Route path="/cadastro-cliente" element={<ClientRegistrationPage />} />
      <Route path="/sem-permissao" element={<ForbiddenPage />} />

      {/* Rotas autenticadas, dentro do layout com sidebar */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />

          {/* Exemplo de restrição por perfil: apenas admin do estabelecimento */}
          <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
            <Route path="/funcionarios" element={<EmployeesListPage />} />
            <Route path="/servicos" element={<ServicesPage />} />
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['CLIENT']} />}>
            <Route path="/agendamentos" element={<AppointmentsPage />} />
            <Route path="/estabelecimentos/:establishmentId/agendar" element={<BookingPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}

export default App

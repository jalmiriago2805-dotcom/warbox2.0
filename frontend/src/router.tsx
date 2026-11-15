import { createBrowserRouter } from 'react-router'
import { AppLayout } from '@/components/layout/AppLayout'
import { ActivateCreatorPage } from '@/pages/ActivateCreatorPage'
import { CreatorPublicPage } from '@/pages/CreatorPublicPage'
import { EditCreatorProfilePage } from '@/pages/EditCreatorProfilePage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { HomePage } from '@/pages/HomePage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SignupPage } from '@/pages/SignupPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'cadastro', element: <SignupPage /> },
      { path: 'entrar', element: <LoginPage /> },
      { path: 'verificar-email', element: <VerifyEmailPage /> },
      { path: 'esqueci-senha', element: <ForgotPasswordPage /> },
      { path: 'redefinir-senha', element: <ResetPasswordPage /> },
      { path: 'c/:handle', element: <CreatorPublicPage /> },
      { path: 'criador/ativar', element: <ActivateCreatorPage /> },
      { path: 'criador/editar', element: <EditCreatorProfilePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

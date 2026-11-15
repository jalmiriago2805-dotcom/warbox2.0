import { createBrowserRouter } from 'react-router'
import { AppLayout } from '@/components/layout/AppLayout'
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
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

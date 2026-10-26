import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ToastProvider from './components/toast/ToastProvider'
import ErrorBoundary from './components/ErrorBoundary'
import PwaStatus from './components/PwaStatus'
import './index.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <ErrorBoundary>
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <App />
          <PwaStatus />
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  </ErrorBoundary>,
)

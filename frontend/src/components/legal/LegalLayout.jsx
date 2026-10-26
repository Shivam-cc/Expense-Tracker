import { Link } from 'react-router-dom'
import { Wallet, ArrowLeft } from 'lucide-react'
import Footer from '../landing/Footer'

export default function LegalLayout({ title, updated, children }) {
  return (
    <div className="min-h-screen bg-white flex flex-col">
      <header className="border-b border-gray-100">
        <div className="max-w-3xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-9 h-9 bg-gradient-to-br from-primary-500 to-primary-700 rounded-lg flex items-center justify-center">
              <Wallet className="w-5 h-5 text-white" />
            </div>
            <span className="text-xl font-bold text-gray-900">ExpenseFlow</span>
          </Link>
          <Link to="/" className="inline-flex items-center gap-1.5 text-sm text-gray-600 hover:text-primary-600">
            <ArrowLeft className="w-4 h-4" /> Back to home
          </Link>
        </div>
      </header>

      <main className="flex-1 px-6 py-12 sm:py-16">
        <article className="max-w-3xl mx-auto">
          <h1 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-2">{title}</h1>
          {updated && <p className="text-sm text-gray-500 mb-10">Last updated: {updated}</p>}
          <div className="space-y-4 text-gray-700 leading-relaxed [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:text-gray-900 [&_h2]:mt-10 [&_h2]:mb-2 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:space-y-1.5 [&_a]:text-primary-600 [&_a:hover]:text-primary-700 [&_a]:underline [&_code]:bg-gray-100 [&_code]:px-1 [&_code]:rounded [&_code]:text-sm">
            {children}
          </div>
        </article>
      </main>

      <Footer />
    </div>
  )
}

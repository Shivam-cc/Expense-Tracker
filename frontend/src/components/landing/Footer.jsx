import { Link } from 'react-router-dom'
import { Wallet, Mail } from 'lucide-react'
import { GITHUB_URL } from '../legal/legalConfig'

const linkCls = 'hover:text-white transition-colors'

function GithubIcon({ className }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className={className}>
      <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.17c-3.2.7-3.87-1.37-3.87-1.37-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.15.08 1.76 1.19 1.76 1.19 1.03 1.76 2.69 1.25 3.35.96.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5Z" />
    </svg>
  )
}

export default function Footer() {
  return (
    <footer className="bg-gray-900 text-gray-400 py-16 sm:py-20 px-6 sm:px-8 lg:px-12">
      <div className="max-w-7xl mx-auto">
        <div className="grid md:grid-cols-4 gap-10 mb-12">
          {/* Brand */}
          <div className="md:col-span-2">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-9 h-9 bg-gradient-to-br from-primary-500 to-primary-700 rounded-lg flex items-center justify-center">
                <Wallet className="w-5 h-5 text-white" />
              </div>
              <span className="text-xl font-bold text-white">ExpenseFlow</span>
            </div>
            <p className="text-sm leading-relaxed max-w-sm">
              A simple, open-source expense tracker. Log what you spend, organise it your way, and export it whenever you like.
            </p>
            <div className="flex gap-3 mt-5">
              <a
                href={GITHUB_URL}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="ExpenseFlow on GitHub"
                className="w-9 h-9 bg-gray-800 rounded-lg flex items-center justify-center hover:bg-gray-700 transition-colors"
              >
                <GithubIcon className="w-4 h-4" />
              </a>
              <Link
                to="/contact"
                aria-label="Contact us"
                className="w-9 h-9 bg-gray-800 rounded-lg flex items-center justify-center hover:bg-gray-700 transition-colors"
              >
                <Mail className="w-4 h-4" />
              </Link>
            </div>
          </div>

          {/* Product */}
          <div>
            <h4 className="text-white font-semibold mb-4">Product</h4>
            <ul className="space-y-2.5 text-sm">
              <li><a href="/#features" className={linkCls}>Features</a></li>
              <li><a href="/#how-it-works" className={linkCls}>How It Works</a></li>
              <li><a href="/#why" className={linkCls}>Why ExpenseFlow</a></li>
              <li><Link to="/register" className={linkCls}>Create Account</Link></li>
            </ul>
          </div>

          {/* Legal & help */}
          <div>
            <h4 className="text-white font-semibold mb-4">Legal &amp; Help</h4>
            <ul className="space-y-2.5 text-sm">
              <li><Link to="/privacy" className={linkCls}>Privacy Policy</Link></li>
              <li><Link to="/terms" className={linkCls}>Terms of Service</Link></li>
              <li><Link to="/security" className={linkCls}>Security</Link></li>
              <li><Link to="/contact" className={linkCls}>Contact</Link></li>
            </ul>
          </div>
        </div>

        {/* Bottom */}
        <div className="pt-8 border-t border-gray-800 text-center text-sm">
          <p>&copy; {new Date().getFullYear()} ExpenseFlow. All rights reserved.</p>
        </div>
      </div>
    </footer>
  )
}

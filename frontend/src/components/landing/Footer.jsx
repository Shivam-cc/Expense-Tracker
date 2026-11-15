import { Link } from 'react-router-dom'
import { Wallet, Mail } from 'lucide-react'
import { GITHUB_URL } from '../legal/legalConfig'
import './landing.css'

function GithubIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.1.79-.25.79-.56v-2.17c-3.2.7-3.87-1.37-3.87-1.37-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.15.08 1.76 1.19 1.76 1.19 1.03 1.76 2.69 1.25 3.35.96.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5Z" />
    </svg>
  )
}

export default function Footer() {
  return (
    <footer className="ef ef-footer">
      <div className="ef-container">
        <div className="ef-footer__grid">
          <div>
            <Link to="/" className="ef-brand" aria-label="ExpenseFlow home">
              <span className="ef-brand__tile" aria-hidden="true"><Wallet /></span>
              ExpenseFlow
            </Link>
            <p style={{ marginTop: 'var(--space-stack)' }}>
              A simple, open-source expense tracker. Log what you spend, organise it your way, and export it whenever you like.
            </p>
            <div className="ef-footer__icons">
              <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" aria-label="ExpenseFlow on GitHub" className="ef-footer__icon">
                <GithubIcon />
              </a>
              <Link to="/contact" aria-label="Contact us" className="ef-footer__icon">
                <Mail aria-hidden="true" />
              </Link>
            </div>
          </div>

          <nav aria-label="Product">
            <h2 className="ef-footer__title">Product</h2>
            <ul>
              <li><a href="/#features" className="ef-footer__link">Tools</a></li>
              <li><a href="/#how-it-works" className="ef-footer__link">How it works</a></li>
              <li><a href="/#why" className="ef-footer__link">Why ExpenseFlow</a></li>
              <li><Link to="/register" className="ef-footer__link">Create account</Link></li>
            </ul>
          </nav>

          <nav aria-label="Legal and help">
            <h2 className="ef-footer__title">Legal &amp; help</h2>
            <ul>
              <li><Link to="/privacy" className="ef-footer__link">Privacy Policy</Link></li>
              <li><Link to="/terms" className="ef-footer__link">Terms of Service</Link></li>
              <li><Link to="/security" className="ef-footer__link">Security</Link></li>
              <li><Link to="/contact" className="ef-footer__link">Contact</Link></li>
            </ul>
          </nav>
        </div>

        <p className="ef-footer__legal">&copy; {new Date().getFullYear()} ExpenseFlow. All rights reserved.</p>
      </div>
    </footer>
  )
}

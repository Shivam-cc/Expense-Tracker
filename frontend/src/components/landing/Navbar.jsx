import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Menu, X, Wallet } from 'lucide-react'
import { CtaLink } from './ui'

const navLinks = [
  { href: '#features', label: 'Tools' },
  { href: '#platforms', label: 'Where it runs' },
  { href: '#how-it-works', label: 'How it works' },
  { href: '#why', label: 'Why ExpenseFlow' },
  { href: '#faq', label: 'FAQ' },
]

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false)

  useEffect(() => {
    if (!isOpen) return undefined
    const onKeyDown = (e) => {
      if (e.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [isOpen])

  return (
    <nav className="ef-nav" aria-label="Main">
      <div className="ef-container">
        <div className="ef-nav__bar">
          <Link to="/" className="ef-brand" aria-label="ExpenseFlow home">
            <span className="ef-brand__tile" aria-hidden="true"><Wallet /></span>
            ExpenseFlow
          </Link>

          <div className="ef-nav__links">
            {navLinks.map((l) => (
              <a key={l.href} href={l.href} className="ef-navlink">{l.label}</a>
            ))}
          </div>

          <div className="ef-nav__cta">
            <Link to="/login" className="ef-navlink">Sign in</Link>
            <CtaLink to="/register">Get started</CtaLink>
          </div>

          <button
            type="button"
            className="ef-menu-btn"
            onClick={() => setIsOpen(!isOpen)}
            aria-label={isOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={isOpen}
            aria-controls="ef-mobile-menu"
          >
            {isOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
          </button>
        </div>

        {isOpen && (
          <div id="ef-mobile-menu" className="ef-mobile">
            {navLinks.map((l) => (
              <a key={l.href} href={l.href} onClick={() => setIsOpen(false)} className="ef-navlink">{l.label}</a>
            ))}
            <Link to="/login" className="ef-navlink">Sign in</Link>
            <CtaLink to="/register">Get started</CtaLink>
          </div>
        )}
      </div>
    </nav>
  )
}

import { Link } from 'react-router-dom'
import { Lock, Download, Code, Trash2 } from 'lucide-react'
import { GITHUB_URL } from '../legal/legalConfig'


const principles = [
  { icon: Lock, title: 'Private by design', description: 'We collect only what the app needs. No ads, and your expense data is never sold.' },
  { icon: Download, title: 'Your data is portable', description: 'Export your expenses to CSV whenever you want. No lock-in.' },
  { icon: Trash2, title: 'Delete anytime', description: 'You can delete your account and its data from Settings once you are signed in.' },
  { icon: Code, title: 'Open source', description: 'The code is public on GitHub, so you can see exactly how the app works.' },
]

export default function WhyExpenseFlow() {
  return (
    <section id="why" className="ef-section ef-section--canvas ef-anchor" aria-labelledby="why-title" style={{ paddingTop: 0 }}>
      <div className="ef-container ef-why">
        <div className="ef-copy">
          <h2 id="why-title" className="ef-h2">How your data is handled</h2>
          <p className="ef-lede">
            Money data is personal. Read the{' '}
            <Link to="/privacy" className="ef-link">Privacy Policy</Link> and the{' '}
            <Link to="/security" className="ef-link">Security page</Link> for the details, or browse the{' '}
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="ef-link">source code</a>.
          </p>
        </div>

        <ul className="ef-reasons">
          {principles.map((p) => (
            <li key={p.title} className="ef-reason">
              <span className="ef-reason__icon" aria-hidden="true"><p.icon /></span>
              <h3 className="ef-h3">{p.title}</h3>
              <p className="ef-small">{p.description}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

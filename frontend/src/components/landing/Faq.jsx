import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'

const faqs = [
  { q: 'Is ExpenseFlow free?', a: <>Yes. Create an account and start logging expenses. No credit card is required.</> },
  { q: 'Can I export my data?', a: <>Yes. Export your expenses to CSV whenever you want, with the date range and category filters you have applied.</> },
  { q: 'Can I install it on my phone or desktop?', a: <>Yes. ExpenseFlow installs from your browser as an app on mobile and desktop, and there is also an Android app.</> },
  { q: 'Can I delete my account?', a: <>Yes. Once you are signed in, you can delete your account and its data from Settings.</> },
  { q: 'How is my data protected?', a: <>Passwords are hashed with BCrypt, email is verified with a one-time code, and everything runs over HTTPS. The <Link to="/security" className="ef-link">Security page</Link> has the details.</> },
  { q: 'Does it support budgets?', a: <>Not yet. Budgets are on the roadmap, so today ExpenseFlow focuses on logging, categories, summaries and export.</> },
]

export default function Faq() {
  const [open, setOpen] = useState(null)
  const base = useId()

  return (
    <section id="faq" className="ef-section ef-section--surface ef-anchor" aria-labelledby="faq-title">
      <div className="ef-container ef-faq">
        <div className="ef-copy">
          <h2 id="faq-title" className="ef-h2">Common questions</h2>
          <p className="ef-lede">
            Something missing? <Link to="/contact" className="ef-link">Get in touch</Link>.
          </p>
        </div>

        <div className="ef-acc">
          {faqs.map((f, i) => {
            const isOpen = open === i
            const btnId = `${base}-q${i}`
            const panelId = `${base}-a${i}`
            return (
              <div key={f.q} className="ef-acc__item">
                <h3 className="ef-acc__q">
                  <button
                    type="button"
                    id={btnId}
                    className="ef-acc__btn"
                    aria-expanded={isOpen}
                    aria-controls={panelId}
                    onClick={() => setOpen(isOpen ? null : i)}
                  >
                    {f.q}
                    <span className="ef-acc__icon" aria-hidden="true"><Plus /></span>
                  </button>
                </h3>
                <div id={panelId} role="region" aria-labelledby={btnId} className="ef-acc__panel" data-open={isOpen}>
                  <div className="ef-acc__inner">
                    <div className="ef-small">{f.a}</div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

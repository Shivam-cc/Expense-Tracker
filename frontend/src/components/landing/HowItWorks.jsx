import { Capture } from './ui'

const steps = [
  {
    step: '01',
    title: 'Create your account',
    description: 'Sign up with your email and confirm it with a 6-digit code we send you.',
    capture: { name: 'steps_register_capture', alt: 'The ExpenseFlow sign-up form with name, email and password fields.' },
  },
  {
    step: '02',
    title: 'Add an expense',
    description: 'Log an amount, a date and optional notes, and file it under one of your own categories.',
    capture: { name: 'steps_add_expense_capture', alt: 'The Add Expense dialog with a description, amount, date and category filled in.' },
  },
  {
    step: '03',
    title: 'Review and export',
    description: 'Check monthly summaries and charts, filter by date or category, and export to CSV.',
    capture: { name: 'steps_insights_capture', alt: 'The spending-by-category chart and daily trend on the dashboard, shown with sample data.' },
  },
]

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="ef-section ef-section--canvas ef-anchor" aria-labelledby="how-title">
      <div className="ef-container">
        <div className="ef-head">
          <h2 id="how-title" className="ef-h2">Three steps from sign-up to your first summary</h2>
        </div>

        <ol className="ef-steps">
          {steps.map((s) => (
            <li key={s.step} className="ef-card ef-step">
              <Capture name={s.capture.name} width={1120} height={1400} alt={s.capture.alt} />
              <div className="ef-card__body">
                <span className="ef-step__num" aria-hidden="true">{s.step}</span>
                <h3 className="ef-h3">{s.title}</h3>
                <p className="ef-small">{s.description}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

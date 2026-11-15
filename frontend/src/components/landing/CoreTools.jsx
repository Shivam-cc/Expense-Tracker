import { Capture } from './ui'

const trio = [
  { title: 'Custom categories', text: 'Create your own categories and give each one an icon and a colour so your spending is easy to scan.' },
  { title: 'Search and filter', text: 'Search by description, then narrow by date range or category to find exactly what you need.' },
  { title: 'CSV export', text: 'Take your expenses with you. Export to CSV whenever you want, with no lock-in.' },
]

export default function CoreTools() {
  return (
    <section id="features" className="ef-section ef-section--canvas ef-anchor" aria-labelledby="tools-title">
      <div className="ef-container">
        <div className="ef-head">
          <h2 id="tools-title" className="ef-h2">What you can do with it</h2>
        </div>

        <div className="ef-bento">
          <div className="ef-bento__pair">
            <article className="ef-card">
              <Capture
                name="feature_add_expense_capture"
                width={1920}
                height={1440}
                className="ef-capture--43"
                alt="The Add Expense dialog filled in with a description, amount, date, category and notes."
              />
              <div className="ef-card__body">
                <h3 className="ef-h3">Add expenses in seconds</h3>
                <p className="ef-small">Description, amount, date, category and optional notes, with inline validation.</p>
              </div>
            </article>
            <article className="ef-card">
              <Capture
                name="feature_spending_charts_capture"
                width={2160}
                height={1215}
                className="ef-capture--169"
                alt="A donut chart of spending by category beside a bar chart of daily spending, shown with sample data."
              />
              <div className="ef-card__body">
                <h3 className="ef-h3">See where the money goes</h3>
                <p className="ef-small">A monthly total, a spending-by-category breakdown and a daily trend for the date range you pick.</p>
              </div>
            </article>
          </div>

          <ul className="ef-bento__trio">
            {trio.map((t) => (
              <li key={t.title} className="ef-rule">
                <h3 className="ef-h3">{t.title}</h3>
                <p className="ef-small">{t.text}</p>
              </li>
            ))}
          </ul>
        </div>

        <p className="ef-small" style={{ marginTop: 'var(--space-stack)' }}>
          Budgets are on the roadmap. They are not available yet.
        </p>
      </div>
    </section>
  )
}

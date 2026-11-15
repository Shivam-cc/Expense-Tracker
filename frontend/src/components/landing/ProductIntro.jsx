import { CtaLink, Capture } from './ui'

export default function ProductIntro() {
  return (
    <section className="ef-section ef-section--surface" aria-labelledby="intro-title">
      <div className="ef-container ef-split">
        <div className="ef-copy">
          <h2 id="intro-title" className="ef-h2">Every expense in one searchable list</h2>
          <p className="ef-lede">
            Add an expense with an amount, a date and optional notes. Search by description and
            find last Tuesday's lunch as fast as you logged it.
          </p>
          <ul className="ef-ticks" style={{ width: '100%' }}>
            <li>Add, edit and delete expenses in your own currency</li>
            <li>Search by description, filter by date range or category</li>
            <li>Export the whole list to CSV any time</li>
          </ul>
          <div><CtaLink to="/register" variant="secondary">Create an account</CtaLink></div>
        </div>

        <div className="ef-hero-frame ef-intro-frame">
          <Capture
            name="intro_expense_list_capture"
            width={1350}
            height={1800}
            alt="The expense list in ExpenseFlow with search, category and sort controls above rows of sample expenses."
          />
        </div>
      </div>
    </section>
  )
}

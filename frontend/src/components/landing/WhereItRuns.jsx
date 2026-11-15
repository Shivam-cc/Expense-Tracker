import { CtaLink, Capture } from './ui'
import { GITHUB_URL } from '../legal/legalConfig'

const androidRows = ['Expense dialog with a date picker', 'Category colours on every row', 'Inline validation as you type']

export default function WhereItRuns() {
  return (
    <section id="platforms" className="ef-section ef-section--yellow ef-anchor" aria-labelledby="platforms-title">
      <div className="ef-container">
        <div className="ef-head">
          <h2 id="platforms-title" className="ef-h2">Browser, installed app, Android</h2>
          <p className="ef-lede">
            Use ExpenseFlow in any modern browser, install it as an app on mobile or desktop, or run the Android app.
          </p>
        </div>

        <div className="ef-run">
          <figure className="ef-hero-frame">
            <Capture
              name="showcase_web_dashboard_capture"
              width={1536}
              height={1920}
              alt="The ExpenseFlow dashboard in a desktop browser window, with summary cards and charts."
            />
          </figure>
          <figure className="ef-hero-frame">
            <Capture
              name="showcase_installed_app_capture"
              width={1200}
              height={1500}
              alt="ExpenseFlow installed as an app on a phone, showing the expense list."
            />
          </figure>

          <article className="ef-android">
            <div className="ef-copy">
              <h3 className="ef-h2">The Android app</h3>
              <p className="ef-lede">A native app that works with the same account and the same data.</p>
              <CtaLink href={GITHUB_URL} target="_blank" rel="noopener noreferrer" variant="secondary">
                View the source
                <span className="sr-only"> (opens in a new tab)</span>
              </CtaLink>
            </div>
            <ul className="ef-android__list">
              {androidRows.map((row) => (
                <li key={row} className="ef-android__row">
                  <span className="ef-android__dot" aria-hidden="true" />
                  {row}
                </li>
              ))}
            </ul>
          </article>
        </div>
      </div>
    </section>
  )
}

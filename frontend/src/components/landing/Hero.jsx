import { CtaLink, Capture } from './ui'

export default function Hero() {
  return (
    <section className="ef-hero" aria-labelledby="hero-title">
      <div className="ef-container">
        <h1 id="hero-title" className="ef-display ef-enter">Know where your money goes</h1>

        <div className="ef-hero__row">
          <p className="ef-lede ef-enter" style={{ '--ef-delay': '270ms' }}>
            Log what you spend, sort it into categories you name yourself, and read the month back as totals and
            charts. It runs in the browser, installs on your phone, and exports to CSV.
          </p>
          <div className="ef-actions ef-enter" style={{ '--ef-delay': '360ms' }}>
            <CtaLink to="/register">Start tracking free</CtaLink>
            <CtaLink to="/login" variant="secondary">Sign in</CtaLink>
          </div>
        </div>

        <div className="ef-hero-frame ef-enter" style={{ '--ef-delay': '450ms' }}>
          <Capture
            name="hero_dashboard_capture"
            width={2160}
            height={1350}
            priority
            alt="The ExpenseFlow dashboard with monthly totals, a spending-by-category chart and a daily trend chart, shown with sample data."
          />
        </div>
      </div>
    </section>
  )
}

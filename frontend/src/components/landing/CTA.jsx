import { CtaLink } from './ui'

export default function CTA() {
  return (
    <section className="ef-section ef-section--dark ef-cta" aria-labelledby="cta-title">
      <div className="ef-container ef-cta__inner">
        <h2 id="cta-title" className="ef-h1">Create an account and log your first expense?</h2>
        <p className="ef-lede">It takes a minute and needs no credit card.</p>
        <div className="ef-actions">
          <CtaLink to="/register">Get started free</CtaLink>
          <CtaLink to="/login" variant="secondary">Sign in</CtaLink>
        </div>
      </div>
    </section>
  )
}

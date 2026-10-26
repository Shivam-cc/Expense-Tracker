// TODO: replace with legal-reviewed text / company name / contact email
// This is a plain-English template, not legal advice.
import { Link } from 'react-router-dom'
import LegalLayout from '../components/legal/LegalLayout'
import { CONTACT_EMAIL, LEGAL_LAST_UPDATED, OPERATOR_NAME } from '../components/legal/legalConfig'

export default function TermsPage() {
  return (
    <LegalLayout title="Terms of Service" updated={LEGAL_LAST_UPDATED}>
      <p>
        These terms govern your use of ExpenseFlow, operated by {OPERATOR_NAME}. By creating an account or using the
        service you agree to them and to our <Link to="/privacy">Privacy Policy</Link>.
      </p>

      <h2>1. The service</h2>
      <p>
        ExpenseFlow is a personal expense-tracking tool: you record expenses, organise them into categories, view
        summaries and charts, and export your data. It is not financial, tax, legal, or accounting advice, and it does
        not connect to your bank.
      </p>

      <h2>2. Your account</h2>
      <ul>
        <li>You must be at least 18 and provide a valid email address that you control.</li>
        <li>Keep your password confidential. You are responsible for activity under your account.</li>
        <li>Tell us promptly if you suspect unauthorised access.</li>
      </ul>

      <h2>3. Acceptable use</h2>
      <p>You agree not to:</p>
      <ul>
        <li>attempt to break, probe, or overload the service, or bypass rate limits or security controls;</li>
        <li>access another person's data or use the service to violate any law;</li>
        <li>upload malicious content or use automated means to create accounts in bulk.</li>
      </ul>

      <h2>4. Your data</h2>
      <p>
        You own the data you enter. You give us permission to store and process it solely to provide the service. You
        can export your expenses as CSV and delete your account at any time; see the Privacy Policy for what happens
        to your data.
      </p>

      <h2>5. Availability and changes</h2>
      <p>
        The service is provided as-is and may be interrupted, changed, or discontinued. We will try to give notice of
        material changes. Features marked "coming soon" are plans, not commitments.
      </p>

      <h2>6. Disclaimer and liability</h2>
      <p>
        To the maximum extent permitted by law, the service is provided without warranties of any kind, and we are not
        liable for indirect or consequential losses, or for data loss. Keep your own records of anything important.
        Nothing here limits liability that cannot be limited by law.
      </p>

      <h2>7. Termination</h2>
      <p>
        You may stop using the service and delete your account at any time. We may suspend or terminate accounts that
        breach these terms or put the service or other users at risk.
      </p>

      <h2>8. Governing law</h2>
      <p>
        These terms are governed by the laws of India, and the courts of India (subject to any mandatory consumer
        protections in your country of residence) have jurisdiction. TODO: confirm governing law and venue.
      </p>

      <h2>9. Changes to these terms</h2>
      <p>We may update these terms. Continued use after an update means you accept the new terms.</p>

      <h2>10. Contact</h2>
      <p>
        Questions: <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> or the <Link to="/contact">Contact page</Link>.
      </p>
    </LegalLayout>
  )
}

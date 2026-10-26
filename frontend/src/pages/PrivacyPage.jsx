// TODO: replace with legal-reviewed text / company name / contact email
// This is a plain-English template, not legal advice.
import LegalLayout from '../components/legal/LegalLayout'
import { CONTACT_EMAIL, LEGAL_LAST_UPDATED, OPERATOR_NAME } from '../components/legal/legalConfig'
import { Link } from 'react-router-dom'

export default function PrivacyPage() {
  return (
    <LegalLayout title="Privacy Policy" updated={LEGAL_LAST_UPDATED}>
      <p>
        This policy explains what personal data {OPERATOR_NAME} ("we", "us") collects when you use the ExpenseFlow
        web app, why we collect it, who we share it with, and the rights you have. It is written to meet the
        requirements of India's Digital Personal Data Protection Act, 2023 (DPDP Act) and the EU/UK General Data
        Protection Regulation (GDPR).
      </p>

      <h2>1. Who is responsible for your data</h2>
      <p>
        {OPERATOR_NAME} is the data fiduciary (DPDP Act) and data controller (GDPR) for the personal data described
        here. You can reach us at <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
      </p>

      <h2>2. Data we collect</h2>
      <ul>
        <li><strong>Account data:</strong> your name, email address, and a BCrypt hash of your password. We never store your password in readable form.</li>
        <li><strong>Financial entries you create:</strong> expenses (description, amount, date, notes) and the categories (name, icon, colour) you define, plus your currency preference.</li>
        <li><strong>Technical data:</strong> your IP address and request metadata (timestamp, path, status) in server logs and rate-limiting, used to keep the service secure and to debug problems.</li>
        <li><strong>Verification codes:</strong> short-lived one-time codes sent to your email when you sign up or reset your password.</li>
        <li><strong>Error reports (optional):</strong> if error tracking is enabled, technical details about crashes (browser, page, stack trace). We do not intentionally include your expense data.</li>
      </ul>
      <p>We do not ask for bank credentials, card numbers, or government identifiers.</p>

      <h2>3. Why we use it</h2>
      <ul>
        <li>To create and secure your account and verify your email (performance of a contract / your consent).</li>
        <li>To store and display your expenses, categories, summaries, and exports (performance of a contract).</li>
        <li>To prevent abuse, enforce rate limits, and troubleshoot incidents (legitimate interests / legitimate uses).</li>
        <li>To send transactional emails such as verification codes. We do not send marketing email.</li>
      </ul>
      <p>We do not sell your data and we do not use it for advertising.</p>

      <h2>4. Who processes your data</h2>
      <p>We use these service providers (processors) to run ExpenseFlow:</p>
      <ul>
        <li><strong>Railway</strong> – hosts the backend API and the PostgreSQL database.</li>
        <li><strong>Vercel</strong> – hosts the web frontend.</li>
        <li><strong>SendGrid (Twilio)</strong> – delivers verification emails, so your email address and name are sent to it.</li>
        <li><strong>Sentry</strong> – optional error tracking, only if enabled for the deployment.</li>
      </ul>
      <p>
        These providers may process data outside India or the EU/EEA. Where required, we rely on appropriate
        safeguards such as standard contractual clauses. We may also disclose data if required by law.
      </p>

      <h2>5. Cookies and local storage</h2>
      <p>
        We do not use advertising or analytics cookies. The app stores your sign-in session (access token and basic
        profile such as name and email) in your browser's <code>localStorage</code>, and the installable app caches
        its own static files so it can load quickly and work offline. This storage is strictly necessary for the
        app to function. Clearing your browser data or signing out removes it.
      </p>

      <h2>6. How long we keep data</h2>
      <ul>
        <li>Account, expenses, and categories: until you delete your account.</li>
        <li>Verification codes: a few minutes.</li>
        <li>Server logs: kept only as long as needed for security and debugging, typically no more than 30 days.</li>
        <li>Backups: may retain deleted data for a limited period before being overwritten.</li>
      </ul>

      <h2>7. Your rights</h2>
      <p>Depending on where you live, you can:</p>
      <ul>
        <li><strong>Access and portability:</strong> export your expenses as CSV from the dashboard at any time.</li>
        <li><strong>Correction:</strong> edit your profile and entries in the app.</li>
        <li><strong>Erasure:</strong> delete your account and all associated data from Settings, or ask us to do it.</li>
        <li><strong>Withdraw consent / object / restrict processing:</strong> contact us; withdrawing consent may mean we can no longer provide the service.</li>
        <li><strong>Grievance and complaints:</strong> contact us first. You may also complain to the Data Protection Board of India or your local EU/UK supervisory authority.</li>
      </ul>
      <p>We aim to respond within 30 days.</p>

      <h2>8. Security</h2>
      <p>
        Passwords are hashed with BCrypt, traffic is encrypted with HTTPS, and data is stored in PostgreSQL. See our{' '}
        <Link to="/security">Security page</Link> for details. No system is perfectly secure; we will notify
        affected users and authorities as required by law if a breach occurs.
      </p>

      <h2>9. Children</h2>
      <p>ExpenseFlow is not intended for children under 18. If you believe a child has created an account, contact us and we will delete it.</p>

      <h2>10. Changes</h2>
      <p>We may update this policy. We will change the "Last updated" date and, for material changes, notify you in the app or by email.</p>

      <h2>11. Contact</h2>
      <p>
        Questions or requests: <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> or see the{' '}
        <Link to="/contact">Contact page</Link>.
      </p>
    </LegalLayout>
  )
}

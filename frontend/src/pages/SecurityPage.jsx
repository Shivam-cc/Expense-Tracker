// TODO: replace with legal-reviewed text / company name / contact email
import { Link } from 'react-router-dom'
import LegalLayout from '../components/legal/LegalLayout'
import {
  GITHUB_SECURITY_URL,
  LEGAL_LAST_UPDATED,
  SECURITY_EMAIL,
} from '../components/legal/legalConfig'

export default function SecurityPage() {
  return (
    <LegalLayout title="Security" updated={LEGAL_LAST_UPDATED}>
      <p>
        ExpenseFlow is a small project, and we keep security straightforward. This page describes what we actually do
        today; we do not claim certifications such as SOC 2 or ISO 27001.
      </p>

      <h2>How we protect your data</h2>
      <ul>
        <li><strong>Passwords:</strong> hashed with BCrypt (salted, adaptive) before storage. We cannot read your password.</li>
        <li><strong>Email verification:</strong> new accounts must verify their email with a one-time code that expires after a few minutes.</li>
        <li><strong>HTTPS:</strong> the app and API are served over HTTPS, hosted on Vercel and Railway.</li>
        <li><strong>Storage:</strong> your data lives in a PostgreSQL database managed by Railway.</li>
        <li><strong>Sessions:</strong> API requests are authenticated with signed tokens and are scoped to your own data.</li>
        <li><strong>Abuse protection:</strong> request rate limiting and standard security headers are enabled on the API.</li>
      </ul>

      <h2>Your controls</h2>
      <ul>
        <li>Export your expenses as CSV from the dashboard at any time.</li>
        <li>Delete your account and its data from <Link to="/settings">Settings</Link> once signed in.</li>
        <li>Use a unique password that you don't use anywhere else.</li>
      </ul>

      <h2>Responsible disclosure</h2>
      <p>
        If you believe you have found a vulnerability, please report it privately so we can fix it before it is
        public:
      </p>
      <ul>
        <li>Email <a href={`mailto:${SECURITY_EMAIL}`}>{SECURITY_EMAIL}</a>, or</li>
        <li>use <a href={GITHUB_SECURITY_URL} target="_blank" rel="noopener noreferrer">GitHub private vulnerability reporting</a>.</li>
      </ul>
      <p>Please include steps to reproduce, the impact you observed, and any proof of concept. We ask that you:</p>
      <ul>
        <li>give us reasonable time to respond and fix the issue before disclosing it publicly;</li>
        <li>avoid accessing, changing, or deleting other users' data, and avoid denial-of-service testing;</li>
        <li>only test with accounts you own.</li>
      </ul>
      <p>
        We will acknowledge reports within a few business days and keep you updated. We do not run a paid bug bounty
        programme, but we are happy to credit reporters who want it. Acting in good faith under this policy, we will
        not pursue action against you.
      </p>

      <h2>Related</h2>
      <p>
        <Link to="/privacy">Privacy Policy</Link> · <Link to="/terms">Terms of Service</Link> ·{' '}
        <Link to="/contact">Contact</Link>
      </p>
    </LegalLayout>
  )
}

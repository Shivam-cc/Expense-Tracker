// TODO: replace with legal-reviewed text / company name / contact email
import { Link } from 'react-router-dom'
import LegalLayout from '../components/legal/LegalLayout'
import {
  CONTACT_EMAIL,
  GITHUB_ISSUES_URL,
  GITHUB_URL,
  SECURITY_EMAIL,
} from '../components/legal/legalConfig'

export default function ContactPage() {
  return (
    <LegalLayout title="Contact">
      <p>We are a small team and read every message. Pick whichever channel fits best.</p>

      <h2>Support and privacy requests</h2>
      <p>
        Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> for help with your account, data access or
        deletion requests, or questions about the <Link to="/privacy">Privacy Policy</Link>.
      </p>

      <h2>Bugs and feature requests</h2>
      <p>
        ExpenseFlow is open source. Open an issue on <a href={GITHUB_ISSUES_URL} target="_blank" rel="noopener noreferrer">GitHub</a> or
        browse the <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer">repository</a>. Please don't post
        personal or financial data in public issues.
      </p>

      <h2>Security issues</h2>
      <p>
        Report vulnerabilities privately to <a href={`mailto:${SECURITY_EMAIL}`}>{SECURITY_EMAIL}</a>. See the{' '}
        <Link to="/security">Security page</Link> for details.
      </p>
    </LegalLayout>
  )
}

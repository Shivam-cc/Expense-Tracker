import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { Lock, Download, Code, Trash2 } from 'lucide-react'
import { GITHUB_URL } from '../legal/legalConfig'

const principles = [
  {
    icon: Lock,
    title: 'Private by design',
    description: 'We collect only what the app needs. No ads, and your expense data is never sold.',
  },
  {
    icon: Download,
    title: 'Your data is portable',
    description: 'Export your expenses to CSV whenever you want. No lock-in.',
  },
  {
    icon: Trash2,
    title: 'Delete anytime',
    description: 'You can delete your account and its data from Settings once you are signed in.',
  },
  {
    icon: Code,
    title: 'Open source',
    description: 'The code is public on GitHub, so you can see exactly how the app works.',
  },
]

export default function WhyExpenseFlow() {
  return (
    <section id="why" className="py-16 sm:py-24 px-6 sm:px-8 lg:px-12 bg-gradient-to-b from-gray-50/50 to-white">
      <div className="max-w-7xl mx-auto">
        {/* Section Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <span className="text-sm font-semibold text-primary-600 uppercase tracking-wider">Why ExpenseFlow</span>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
            Built on a Few Simple Principles
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            Money data is personal. Here is how we treat it. Read the{' '}
            <Link to="/privacy" className="text-primary-600 hover:text-primary-700 underline">Privacy Policy</Link>{' '}
            and{' '}
            <Link to="/security" className="text-primary-600 hover:text-primary-700 underline">Security page</Link>{' '}
            for the details, or browse the{' '}
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="text-primary-600 hover:text-primary-700 underline">source code</a>.
          </p>
        </motion.div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {principles.map((p, index) => (
            <motion.div
              key={p.title}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: index * 0.1 }}
              className="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm hover:shadow-md transition-shadow"
            >
              <div className="w-12 h-12 bg-primary-50 rounded-xl flex items-center justify-center mb-4">
                <p.icon className="w-6 h-6 text-primary-600" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">{p.title}</h3>
              <p className="text-gray-600 leading-relaxed">{p.description}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

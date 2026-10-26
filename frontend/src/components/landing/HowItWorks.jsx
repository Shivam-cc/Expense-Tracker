import { motion } from 'framer-motion'
import { UserPlus, Receipt, Tags, LineChart } from 'lucide-react'

const steps = [
  {
    icon: UserPlus,
    step: '01',
    title: 'Create Account',
    description: 'Sign up with your email and confirm it with a 6-digit code we send you.',
  },
  {
    icon: Receipt,
    step: '02',
    title: 'Add Expenses',
    description: 'Log each expense with an amount, date, and optional notes, in just a few taps.',
  },
  {
    icon: Tags,
    step: '03',
    title: 'Organise',
    description: 'Group expenses into your own categories, each with an icon and colour.',
  },
  {
    icon: LineChart,
    step: '04',
    title: 'Review & Export',
    description: 'Check monthly summaries and charts, filter by date or category, and export to CSV.',
  },
]

export default function HowItWorks() {
  return (
    <section id="how-it-works" className="py-16 sm:py-24 px-6 sm:px-8 lg:px-12">
      <div className="max-w-7xl mx-auto">
        {/* Section Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <span className="text-sm font-semibold text-primary-600 uppercase tracking-wider">How It Works</span>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
            Get Started in 4 Simple Steps
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            From sign-up to your first summary in a few minutes.
          </p>
        </motion.div>

        {/* Steps */}
        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8">
          {steps.map((step, index) => (
            <motion.div
              key={step.step}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: index * 0.15 }}
              className="relative text-center"
            >
              {/* Connector line */}
              {index < steps.length - 1 && (
                <div className="hidden lg:block absolute top-10 left-[60%] w-[80%] h-[2px] bg-gradient-to-r from-primary-200 to-transparent"></div>
              )}

              <div className="relative inline-flex items-center justify-center w-20 h-20 bg-gradient-to-br from-primary-50 to-primary-100 rounded-2xl mb-5 mx-auto">
                <step.icon className="w-8 h-8 text-primary-600" />
                <span className="absolute -top-2 -right-2 w-7 h-7 bg-primary-600 text-white text-xs font-bold rounded-full flex items-center justify-center">
                  {step.step}
                </span>
              </div>

              <h3 className="text-lg font-semibold text-gray-900 mb-2">{step.title}</h3>
              <p className="text-gray-600 leading-relaxed">{step.description}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

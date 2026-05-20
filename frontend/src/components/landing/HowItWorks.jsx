import { motion } from 'framer-motion'
import { UserPlus, Receipt, LineChart, Target } from 'lucide-react'

const steps = [
  {
    icon: UserPlus,
    step: '01',
    title: 'Create Account',
    description: 'Sign up in seconds with your email or Google account. No credit card required.',
  },
  {
    icon: Receipt,
    step: '02',
    title: 'Add Expenses',
    description: 'Log your daily expenses manually or connect your bank for automatic tracking.',
  },
  {
    icon: LineChart,
    step: '03',
    title: 'View Insights',
    description: 'Get personalized insights and visual reports about your spending habits.',
  },
  {
    icon: Target,
    step: '04',
    title: 'Hit Your Goals',
    description: 'Set savings goals and budgets, then watch as you achieve financial freedom.',
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
            From sign-up to financial clarity in minutes. Here's how easy it is.
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

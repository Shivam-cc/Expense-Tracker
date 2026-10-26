import { motion } from 'framer-motion'
import {
  BarChart3,
  ShieldCheck,
  Receipt,
  Search,
  Tags,
  Smartphone,
} from 'lucide-react'

const features = [
  {
    icon: Receipt,
    title: 'Expenses with Notes',
    description: 'Add, edit, and delete expenses with a description, date, amount, and optional notes. Track in your own currency.',
    bgColor: 'bg-yellow-50',
  },
  {
    icon: Tags,
    title: 'Custom Categories',
    description: 'Create your own categories and give each one an icon and a colour so your spending is easy to scan.',
    bgColor: 'bg-purple-50',
  },
  {
    icon: BarChart3,
    title: 'Summaries & Charts',
    description: 'See your monthly total and a spending-by-category breakdown, so you can spot where the money goes.',
    bgColor: 'bg-primary-50',
  },
  {
    icon: Search,
    title: 'Search, Filter & Export',
    description: 'Search by description, filter by date range or category, and export your expenses to CSV any time.',
    bgColor: 'bg-red-50',
  },
  {
    icon: Smartphone,
    title: 'Installable App',
    description: 'Install ExpenseFlow from your browser as a PWA on mobile or desktop for quick, app-like access.',
    bgColor: 'bg-cyan-50',
  },
  {
    icon: ShieldCheck,
    title: 'Secure by Default',
    description: 'BCrypt-hashed passwords, email verification with a one-time code, and HTTPS everywhere.',
    bgColor: 'bg-accent-50',
  },
]

const containerVariants = {
  hidden: {},
  visible: {
    transition: {
      staggerChildren: 0.1,
    },
  },
}

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5 } },
}

export default function Features() {
  return (
    <section id="features" className="py-16 sm:py-24 px-6 sm:px-8 lg:px-12 bg-gray-50/50">
      <div className="max-w-7xl mx-auto">
        {/* Section Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <span className="text-sm font-semibold text-primary-600 uppercase tracking-wider">Features</span>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
            What You Can Do Today
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            A focused set of tools for recording and understanding your spending.
          </p>
        </motion.div>

        {/* Features Grid */}
        <motion.div
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          className="grid md:grid-cols-2 lg:grid-cols-3 gap-6"
        >
          {features.map((feature) => (
            <motion.div
              key={feature.title}
              variants={itemVariants}
              className="group bg-white rounded-2xl p-6 border border-gray-100 hover:border-primary-200 hover:shadow-lg hover:shadow-primary-50 transition-all duration-300"
            >
              <div className={`w-12 h-12 ${feature.bgColor} rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform`}>
                <feature.icon className="w-6 h-6 text-gray-700" />
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-2">{feature.title}</h3>
              <p className="text-gray-600 leading-relaxed">{feature.description}</p>
            </motion.div>
          ))}
        </motion.div>

        <p className="text-center text-sm text-gray-500 mt-10">
          <span className="inline-block px-2 py-0.5 mr-2 bg-gray-100 text-gray-600 rounded-full text-xs font-medium">Coming soon</span>
          Budgets are on our roadmap. They are not available yet.
        </p>
      </div>
    </section>
  )
}

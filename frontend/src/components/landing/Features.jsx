import { motion } from 'framer-motion'
import { 
  BarChart3, 
  Shield, 
  Zap, 
  Bell, 
  Tags, 
  Smartphone 
} from 'lucide-react'

const features = [
  {
    icon: BarChart3,
    title: 'Visual Analytics',
    description: 'Beautiful charts and graphs that make understanding your spending patterns effortless.',
    color: 'from-primary-500 to-primary-600',
    bgColor: 'bg-primary-50',
  },
  {
    icon: Zap,
    title: 'Instant Tracking',
    description: 'Add expenses in seconds with smart categorization and receipt scanning.',
    color: 'from-yellow-500 to-orange-500',
    bgColor: 'bg-yellow-50',
  },
  {
    icon: Shield,
    title: 'Bank-Level Security',
    description: 'Your financial data is encrypted and protected with enterprise-grade security.',
    color: 'from-accent-500 to-accent-600',
    bgColor: 'bg-accent-50',
  },
  {
    icon: Bell,
    title: 'Smart Alerts',
    description: 'Get notified when you\'re approaching budget limits or unusual spending detected.',
    color: 'from-red-500 to-pink-500',
    bgColor: 'bg-red-50',
  },
  {
    icon: Tags,
    title: 'Custom Categories',
    description: 'Organize expenses your way with fully customizable tags and categories.',
    color: 'from-purple-500 to-indigo-500',
    bgColor: 'bg-purple-50',
  },
  {
    icon: Smartphone,
    title: 'Cross-Platform',
    description: 'Access your finances anywhere — web, mobile, and tablet perfectly synced.',
    color: 'from-cyan-500 to-blue-500',
    bgColor: 'bg-cyan-50',
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
            Everything You Need to Manage Money
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            Powerful features designed to give you complete control over your finances with minimal effort.
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
      </div>
    </section>
  )
}

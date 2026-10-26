import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { ArrowRight, Wallet, Receipt, PieChart, Download, Smartphone } from 'lucide-react'

const sampleCategories = [
  { name: 'Food & Dining', amount: '₹12,400', width: '65%', bar: 'bg-primary-500' },
  { name: 'Transport', amount: '₹8,900', width: '45%', bar: 'bg-accent-500' },
  { name: 'Shopping', amount: '₹7,200', width: '38%', bar: 'bg-purple-500' },
]

export default function Hero() {
  return (
    <section className="pt-36 sm:pt-40 pb-16 sm:pb-20 px-6 sm:px-8 lg:px-12 overflow-hidden">
      <div className="max-w-7xl mx-auto">
        <div className="grid md:grid-cols-2 gap-10 lg:gap-16 items-center">
          {/* Left Content */}
          <motion.div
            initial={{ opacity: 0, x: -30 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6 }}
          >
            <div className="inline-flex items-center gap-2 px-3 py-1.5 bg-primary-50 rounded-full border border-primary-100 mb-6">
              <span className="w-2 h-2 bg-accent-500 rounded-full animate-pulse"></span>
              <span className="text-sm font-medium text-primary-700">Simple Expense Tracking</span>
            </div>

            <h1 className="text-3xl sm:text-4xl lg:text-5xl xl:text-6xl font-bold text-gray-900 leading-[1.15] mb-6">
              Know Where Your{' '}
              <span className="bg-gradient-to-r from-primary-600 to-accent-500 bg-clip-text text-transparent">
                Money Goes
              </span>
            </h1>

            <p className="text-base sm:text-lg text-gray-600 mb-8 max-w-lg leading-relaxed">
              Log expenses in seconds, organise them with your own categories, and see monthly summaries and
              spending-by-category charts. Works on your phone and desktop, and you can export everything to CSV.
            </p>

            <div className="flex flex-col sm:flex-row gap-4">
              <Link to="/register" className="group px-6 py-3.5 bg-primary-600 text-white font-semibold rounded-xl hover:bg-primary-700 transition-all shadow-lg shadow-primary-200 flex items-center justify-center gap-2">
                Start Tracking Free
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </Link>
              <Link to="/login" className="px-6 py-3.5 border-2 border-gray-200 text-gray-700 font-semibold rounded-xl hover:border-primary-300 hover:text-primary-600 transition-all text-center">
                Sign In
              </Link>
            </div>

            {/* Capabilities */}
            <div className="flex flex-wrap gap-x-6 gap-y-3 mt-12 pt-8 border-t border-gray-100 text-sm text-gray-600">
              <span className="inline-flex items-center gap-2"><Receipt className="w-4 h-4 text-primary-500" /> Expenses with notes</span>
              <span className="inline-flex items-center gap-2"><Download className="w-4 h-4 text-primary-500" /> CSV export</span>
              <span className="inline-flex items-center gap-2"><Smartphone className="w-4 h-4 text-primary-500" /> Installable app</span>
            </div>
          </motion.div>

          {/* Right - Dashboard Preview */}
          <motion.div
            initial={{ opacity: 0, x: 30 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="relative max-w-md mx-auto md:max-w-none"
          >
            <div className="relative bg-gradient-to-br from-slate-50 to-primary-50 rounded-3xl p-5 sm:p-6 shadow-[0_20px_60px_-15px_rgba(59,130,246,0.15)] border border-primary-100/60">
              {/* Mock Dashboard */}
              <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100 mb-4">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="font-semibold text-gray-800">Monthly Summary</h3>
                  <span className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded-full font-medium">Sample data</span>
                </div>
                <div className="flex gap-4">
                  <div className="flex-1 bg-red-50 rounded-lg p-3">
                    <div className="flex items-center gap-1 mb-1">
                      <Wallet className="w-3.5 h-3.5 text-red-500" />
                      <span className="text-xs text-red-500 font-medium">Spent this month</span>
                    </div>
                    <p className="text-lg font-bold text-gray-900">₹28,500</p>
                  </div>
                  <div className="flex-1 bg-primary-50 rounded-lg p-3">
                    <div className="flex items-center gap-1 mb-1">
                      <Receipt className="w-3.5 h-3.5 text-primary-600" />
                      <span className="text-xs text-primary-600 font-medium">Expenses</span>
                    </div>
                    <p className="text-lg font-bold text-gray-900">42</p>
                  </div>
                </div>
              </div>

              {/* Mini Chart */}
              <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="font-semibold text-gray-800 text-sm">Spending by Category</h3>
                  <PieChart className="w-4 h-4 text-primary-500" />
                </div>
                <div className="space-y-3">
                  {sampleCategories.map((c) => (
                    <div key={c.name}>
                      <div className="flex justify-between text-xs mb-1">
                        <span className="text-gray-600">{c.name}</span>
                        <span className="font-medium text-gray-800">{c.amount}</span>
                      </div>
                      <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                        <div className={`h-full ${c.bar} rounded-full`} style={{ width: c.width }}></div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Floating element */}
            <motion.div
              animate={{ y: [0, -8, 0] }}
              transition={{ repeat: Infinity, duration: 3 }}
              className="absolute -top-3 -right-2 sm:-top-4 sm:-right-4 bg-white rounded-xl p-2.5 sm:p-3 shadow-lg border border-gray-100 z-10"
            >
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 bg-primary-100 rounded-full flex items-center justify-center">
                  <Smartphone className="w-4 h-4 text-primary-600" />
                </div>
                <div>
                  <p className="text-xs text-gray-500">Install as an app</p>
                  <p className="text-sm font-bold text-primary-600">Mobile &amp; desktop</p>
                </div>
              </div>
            </motion.div>
          </motion.div>
        </div>
      </div>
    </section>
  )
}

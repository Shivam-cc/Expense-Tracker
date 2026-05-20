import { useState } from 'react'
import { motion } from 'framer-motion'
import { Menu, X, Wallet } from 'lucide-react'

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-md border-b border-gray-100">
      <div className="max-w-7xl mx-auto px-6 sm:px-8 lg:px-12">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
          <div className="flex items-center gap-2">
            <div className="w-9 h-9 bg-gradient-to-br from-primary-500 to-primary-700 rounded-lg flex items-center justify-center">
              <Wallet className="w-5 h-5 text-white" />
            </div>
            <span className="text-xl font-bold text-gray-900">ExpenseFlow</span>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            <a href="#features" className="text-gray-600 hover:text-primary-600 transition-colors font-medium">Features</a>
            <a href="#how-it-works" className="text-gray-600 hover:text-primary-600 transition-colors font-medium">How It Works</a>
            <a href="#testimonials" className="text-gray-600 hover:text-primary-600 transition-colors font-medium">Testimonials</a>
          </div>

          {/* CTA Buttons */}
          <div className="hidden md:flex items-center gap-3">
            <button className="px-4 py-2 text-gray-700 font-medium hover:text-primary-600 transition-colors">
              Sign In
            </button>
            <button className="px-5 py-2.5 bg-primary-600 text-white font-medium rounded-lg hover:bg-primary-700 transition-colors shadow-md shadow-primary-200">
              Get Started Free
            </button>
          </div>

          {/* Mobile menu button */}
          <button
            className="md:hidden p-2 rounded-lg hover:bg-gray-100"
            onClick={() => setIsOpen(!isOpen)}
          >
            {isOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
        </div>

        {/* Mobile Navigation */}
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            className="md:hidden py-4 border-t border-gray-100"
          >
            <div className="flex flex-col gap-3">
              <a href="#features" className="px-3 py-2 text-gray-600 hover:text-primary-600 font-medium">Features</a>
              <a href="#how-it-works" className="px-3 py-2 text-gray-600 hover:text-primary-600 font-medium">How It Works</a>
              <a href="#testimonials" className="px-3 py-2 text-gray-600 hover:text-primary-600 font-medium">Testimonials</a>
              <div className="flex flex-col gap-2 pt-3 border-t border-gray-100">
                <button className="px-4 py-2 text-gray-700 font-medium">Sign In</button>
                <button className="px-5 py-2.5 bg-primary-600 text-white font-medium rounded-lg">Get Started Free</button>
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </nav>
  )
}

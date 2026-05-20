import { motion } from 'framer-motion'
import { ArrowRight, Sparkles } from 'lucide-react'

export default function CTA() {
  return (
    <section className="py-16 sm:py-24 px-6 sm:px-8 lg:px-12">
      <div className="max-w-4xl mx-auto">
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="relative bg-gradient-to-br from-primary-600 via-primary-700 to-primary-800 rounded-3xl p-10 sm:p-14 text-center overflow-hidden"
        >
          {/* Background decorations */}
          <div className="absolute inset-0 opacity-10">
            <div className="absolute top-0 left-0 w-40 h-40 bg-white rounded-full -translate-x-1/2 -translate-y-1/2"></div>
            <div className="absolute bottom-0 right-0 w-60 h-60 bg-white rounded-full translate-x-1/3 translate-y-1/3"></div>
          </div>

          <div className="relative z-10">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 bg-white/10 rounded-full border border-white/20 mb-6">
              <Sparkles className="w-4 h-4 text-yellow-300" />
              <span className="text-sm font-medium text-white/90">Free forever for personal use</span>
            </div>

            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-white mb-4 leading-tight">
              Ready to Master Your Finances?
            </h2>

            <p className="text-lg text-primary-100 mb-8 max-w-xl mx-auto">
              Join 50,000+ users who have already taken control of their spending. No credit card required.
            </p>

            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <button className="group px-8 py-4 bg-white text-primary-700 font-bold rounded-xl hover:bg-primary-50 transition-all shadow-lg flex items-center justify-center gap-2">
                Get Started — It's Free
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
              <button className="px-8 py-4 border-2 border-white/30 text-white font-semibold rounded-xl hover:bg-white/10 transition-all">
                Schedule a Demo
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  )
}

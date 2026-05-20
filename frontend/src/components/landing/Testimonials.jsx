import { motion } from 'framer-motion'
import { Star, Quote } from 'lucide-react'

const testimonials = [
  {
    name: 'Sarah Johnson',
    role: 'Freelance Designer',
    avatar: 'SJ',
    content: 'ExpenseFlow completely changed how I manage my freelance income. The visual reports are incredibly helpful for tax season!',
    rating: 5,
  },
  {
    name: 'Marcus Chen',
    role: 'Software Engineer',
    avatar: 'MC',
    content: 'I\'ve tried many expense trackers, but this one sticks. The auto-categorization saves me so much time every week.',
    rating: 5,
  },
  {
    name: 'Priya Patel',
    role: 'Small Business Owner',
    avatar: 'PP',
    content: 'Managing business and personal expenses used to be a nightmare. ExpenseFlow makes it seamless with separate workspaces.',
    rating: 5,
  },
]

export default function Testimonials() {
  return (
    <section id="testimonials" className="py-16 sm:py-24 px-6 sm:px-8 lg:px-12 bg-gradient-to-b from-gray-50/50 to-white">
      <div className="max-w-7xl mx-auto">
        {/* Section Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          <span className="text-sm font-semibold text-primary-600 uppercase tracking-wider">Testimonials</span>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mt-3 mb-4">
            Loved by Thousands
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            See what our users say about transforming their financial habits with ExpenseFlow.
          </p>
        </motion.div>

        {/* Testimonials Grid */}
        <div className="grid md:grid-cols-3 gap-6">
          {testimonials.map((testimonial, index) => (
            <motion.div
              key={testimonial.name}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: index * 0.1 }}
              className="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm hover:shadow-md transition-shadow"
            >
              <Quote className="w-8 h-8 text-primary-200 mb-4" />

              <p className="text-gray-700 leading-relaxed mb-6">{testimonial.content}</p>

              {/* Rating */}
              <div className="flex gap-1 mb-4">
                {[...Array(testimonial.rating)].map((_, i) => (
                  <Star key={i} className="w-4 h-4 fill-yellow-400 text-yellow-400" />
                ))}
              </div>

              {/* Author */}
              <div className="flex items-center gap-3 pt-4 border-t border-gray-100">
                <div className="w-10 h-10 bg-gradient-to-br from-primary-400 to-primary-600 rounded-full flex items-center justify-center">
                  <span className="text-white text-sm font-bold">{testimonial.avatar}</span>
                </div>
                <div>
                  <p className="font-semibold text-gray-900 text-sm">{testimonial.name}</p>
                  <p className="text-gray-500 text-xs">{testimonial.role}</p>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}

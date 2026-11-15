
import Navbar from '../components/landing/Navbar'
import Hero from '../components/landing/Hero'
import ProductIntro from '../components/landing/ProductIntro'
import CoreTools from '../components/landing/CoreTools'
import WhereItRuns from '../components/landing/WhereItRuns'
import HowItWorks from '../components/landing/HowItWorks'
import WhyExpenseFlow from '../components/landing/WhyExpenseFlow'
import Faq from '../components/landing/Faq'
import CTA from '../components/landing/CTA'
import Footer from '../components/landing/Footer'
import '../components/landing/landing.css'

export default function LandingPage() {


  return (
    <div className="ef min-h-screen">
      <Navbar />
      <main>
        <Hero />
        <ProductIntro />
        <CoreTools />
        <WhereItRuns />
        <HowItWorks />
        <WhyExpenseFlow />
        <Faq />
        <CTA />
      </main>
      <Footer />
    </div>
  )
}

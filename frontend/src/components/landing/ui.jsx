import { Link } from 'react-router-dom'
import './landing.css'

export function CtaLink({ to, href, variant = 'primary', children, ...rest }) {
  const className = `ef-btn ef-btn--${variant}`
  if (href) return <a className={className} href={href} {...rest}>{children}</a>
  return <Link className={className} to={to} {...rest}>{children}</Link>
}

export function Capture({ name, width, height, alt, className = '', priority = false }) {
  return (
    <div className={`ef-capture ${className}`}>
      <img
        src={`/captures/${name}.png`}
        width={width}
        height={height}
        alt={alt}
        loading={priority ? 'eager' : 'lazy'}
        fetchPriority={priority ? 'high' : undefined}
        decoding="async"
      />
    </div>
  )
}

import { Link } from 'react-router-dom'

/**
 * A screen with nothing on it yet.
 *
 * Always carries an action, because an empty state is the most persuasive place in a
 * product to say what to do next, and "No data" is the least useful sentence a screen
 * can contain. The icon is decorative and hidden from assistive technology; the
 * heading carries the meaning.
 *
 * @param {object} props
 * @param {Function} props.icon        a lucide icon component
 * @param {string} props.title         what is missing, in the user's terms
 * @param {string} props.detail        why it is worth adding
 * @param {string} [props.actionTo]    route for the primary action
 * @param {string} [props.actionLabel] its label
 * @param {import('react').ReactNode} [props.children] a custom action instead of a link
 */
export default function EmptyState({
  icon: Icon,
  title,
  detail,
  actionTo,
  actionLabel,
  children,
}) {
  return (
    <div className="panel flex flex-col items-center px-6 py-12 text-center">
      {Icon ? (
        <span className="flex h-11 w-11 items-center justify-center rounded-full bg-brand-600/10 text-brand-600">
          <Icon size={20} aria-hidden="true" />
        </span>
      ) : null}

      <h2 className="mt-5 text-base font-semibold">{title}</h2>
      <p className="mt-2 max-w-sm text-sm text-ink-muted">{detail}</p>

      {children ?? (actionTo ? (
        <Link to={actionTo} className="btn btn-primary mt-6">
          {actionLabel}
        </Link>
      ) : null)}
    </div>
  )
}

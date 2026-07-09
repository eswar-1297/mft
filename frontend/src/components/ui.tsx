import type { ReactNode } from 'react'
import { Loader2, X } from 'lucide-react'
import type { TransferStatus } from '../lib/types'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`card p-5 ${className}`}>{children}</div>
}

export function Spinner({ className = '' }: { className?: string }) {
  return <Loader2 className={`animate-spin ${className}`} size={18} />
}

export function StatCard({
  label,
  value,
  sub,
  accent = false,
  danger = false,
}: {
  label: string
  value: ReactNode
  sub?: string
  accent?: boolean
  danger?: boolean
}) {
  return (
    <div className="card p-5">
      <div className="text-xs font-medium uppercase tracking-wide text-muted">{label}</div>
      <div
        className={`mt-2 text-3xl font-semibold ${
          danger ? 'text-danger' : accent ? 'text-brand' : 'text-ink dark:text-slate-100'
        }`}
      >
        {value}
      </div>
      {sub && <div className="mt-1 text-xs text-muted">{sub}</div>}
    </div>
  )
}

const STATUS_STYLES: Record<TransferStatus | string, string> = {
  SUCCEEDED: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  DELIVERED: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
  RUNNING: 'bg-blue-100 text-brand dark:bg-blue-900/40 dark:text-blue-300',
  PENDING: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
  FAILED: 'bg-red-100 text-danger dark:bg-red-900/40 dark:text-red-300',
}

export function StatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.PENDING
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${style}`}>
      {status}
    </span>
  )
}

export function Modal({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="card w-full max-w-lg p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button className="text-muted hover:text-ink dark:hover:text-white" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-sm text-danger dark:border-red-900/50 dark:bg-red-900/20">
      {message}
    </div>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="py-10 text-center text-sm text-muted">{children}</div>
}

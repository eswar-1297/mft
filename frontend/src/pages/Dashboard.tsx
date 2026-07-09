import { Link } from 'react-router-dom'
import { AlertTriangle } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Page, Transfer } from '../lib/types'
import { Card, EmptyState, ErrorBanner, StatCard, StatusBadge } from '../components/ui'
import {
  durationSeconds,
  formatBytes,
  formatDuration,
  formatRelative,
} from '../lib/format'

export default function Dashboard() {
  // Poll the most recent transfers every 3s so the board moves live during a demo.
  const { data, error } = usePoll<Page<Transfer>>(
    () => api('/api/transfers?size=100'),
    3000,
  )

  const transfers = data?.content ?? []
  const today = transfers.filter((t) => isToday(t.createdAt))
  const succeeded = today.filter((t) => t.status === 'SUCCEEDED')
  const failed = transfers.filter((t) => t.status === 'FAILED')
  const inFlight = transfers.filter((t) => t.status === 'RUNNING' || t.status === 'PENDING')
  const bytesToday = succeeded.reduce((sum, t) => sum + t.bytesTransferred, 0)
  const successRate = today.length ? Math.round((succeeded.length / today.length) * 100) : 100

  // MTTR: mean time from start to completion for the transfers that ended (our reliability stat).
  const durations = transfers
    .map((t) => durationSeconds(t.startedAt, t.completedAt))
    .filter((d): d is number => d !== null)
  const mttr = durations.length
    ? durations.reduce((a, b) => a + b, 0) / durations.length
    : null

  const recentFailure = failed[0]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Operations Dashboard</h1>
          <p className="text-sm text-muted">Live view of your managed file transfers.</p>
        </div>
      </div>

      <ErrorBanner message={error} />

      {recentFailure && (
        <div className="flex items-center justify-between rounded-xl border border-red-200 bg-red-50 px-5 py-4 dark:border-red-900/50 dark:bg-red-900/20">
          <div className="flex items-center gap-3">
            <AlertTriangle className="text-danger" size={22} />
            <div>
              <div className="font-medium text-danger">Transfer failed: {recentFailure.filename}</div>
              <div className="text-sm text-muted">
                {recentFailure.errorMessage || 'See transfer detail'} · {recentFailure.attempts} attempts
              </div>
            </div>
          </div>
          <Link to="/transfers" className="btn-primary">
            Investigate
          </Link>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatCard label="Transfers today" value={today.length} />
        <StatCard label="Success rate" value={`${successRate}%`} accent />
        <StatCard label="In flight" value={inFlight.length} sub="running or queued" />
        <StatCard label="Data moved today" value={formatBytes(bytesToday)} />
        <StatCard
          label="MTTR"
          value={formatDuration(mttr)}
          sub="target < 15 min"
          danger={mttr !== null && mttr > 900}
        />
      </div>

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-semibold">Recent activity</h2>
          <Link to="/transfers" className="text-sm font-medium text-brand hover:underline">
            View all
          </Link>
        </div>
        {transfers.length === 0 ? (
          <EmptyState>
            No transfers yet. Head to <Link to="/send" className="text-brand">Ad-hoc Send</Link> or
            start one from the Transfers page.
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs uppercase tracking-wide text-muted dark:border-slate-800">
                  <th className="pb-2 font-medium">File</th>
                  <th className="pb-2 font-medium">Direction</th>
                  <th className="pb-2 font-medium">Status</th>
                  <th className="pb-2 font-medium">Size</th>
                  <th className="pb-2 font-medium">When</th>
                </tr>
              </thead>
              <tbody>
                {transfers.slice(0, 8).map((t) => (
                  <tr key={t.id} className="border-b border-slate-50 dark:border-slate-800/50">
                    <td className="py-2.5 font-medium">{t.filename}</td>
                    <td className="py-2.5 text-muted">{t.direction.replace('SFTP_', 'SFTP ')}</td>
                    <td className="py-2.5">
                      <StatusBadge status={t.status} />
                    </td>
                    <td className="py-2.5 text-muted">{formatBytes(t.bytesTransferred)}</td>
                    <td className="py-2.5 text-muted">{formatRelative(t.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function isToday(iso: string): boolean {
  const d = new Date(iso)
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  )
}

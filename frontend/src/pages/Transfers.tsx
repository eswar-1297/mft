import { useState } from 'react'
import { Download, RefreshCw, Upload, ShieldCheck } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Page, SftpDetails, Transfer, As2Partner } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, StatusBadge, Spinner } from '../components/ui'
import { formatBytes, formatDateTime } from '../lib/format'

const BLANK_SFTP: SftpDetails = { host: '', port: 22, username: '', password: '' }

export default function Transfers() {
  const { data, error, refresh } = usePoll<Page<Transfer>>(() => api('/api/transfers?size=100'), 3000)
  const [pushOpen, setPushOpen] = useState(false)
  const [pullOpen, setPullOpen] = useState(false)
  const [as2Open, setAs2Open] = useState(false)
  const [selected, setSelected] = useState<Transfer | null>(null)

  const transfers = data?.content ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Transfers</h1>
          <p className="text-sm text-muted">Durable, auto-retrying file movements.</p>
        </div>
        <div className="flex gap-2">
          <button className="btn-ghost" onClick={() => setPullOpen(true)}>
            <Download size={16} /> Pull from partner
          </button>
          <button className="btn-ghost" onClick={() => setAs2Open(true)}>
            <ShieldCheck size={16} /> AS2 Send
          </button>
          <button className="btn-primary" onClick={() => setPushOpen(true)}>
            <Upload size={16} /> Push to partner
          </button>
        </div>
      </div>

      <ErrorBanner message={error} />

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold">{transfers.length} transfers</h2>
          <button className="btn-ghost !py-1 text-xs" onClick={refresh}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
        {transfers.length === 0 ? (
          <EmptyState>No transfers yet. Start one above.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs uppercase tracking-wide text-muted dark:border-slate-800">
                  <th className="pb-2 font-medium">File</th>
                  <th className="pb-2 font-medium">Direction</th>
                  <th className="pb-2 font-medium">Status</th>
                  <th className="pb-2 font-medium">Attempts</th>
                  <th className="pb-2 font-medium">Size</th>
                  <th className="pb-2 font-medium">Created</th>
                </tr>
              </thead>
              <tbody>
                {transfers.map((t) => (
                  <tr
                    key={t.id}
                    className="cursor-pointer border-b border-slate-50 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-800/40"
                    onClick={() => setSelected(t)}
                  >
                    <td className="py-2.5 font-medium">{t.filename}</td>
                    <td className="py-2.5 text-muted">{t.direction.replace('_', ' ')}</td>
                    <td className="py-2.5">
                      <StatusBadge status={t.status} />
                    </td>
                    <td className="py-2.5 text-muted">{t.attempts}</td>
                    <td className="py-2.5 text-muted">{formatBytes(t.bytesTransferred)}</td>
                    <td className="py-2.5 text-muted">{formatDateTime(t.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <PushModal open={pushOpen} onClose={() => setPushOpen(false)} onStarted={refresh} />
      <PullModal open={pullOpen} onClose={() => setPullOpen(false)} onStarted={refresh} />
      <As2SendModal open={as2Open} onClose={() => setAs2Open(false)} onStarted={refresh} />
      <DetailDrawer transfer={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

function SftpFields({
  sftp,
  setSftp,
}: {
  sftp: SftpDetails
  setSftp: (s: SftpDetails) => void
}) {
  return (
    <div className="grid grid-cols-2 gap-3">
      <div className="col-span-2">
        <label className="label">Partner SFTP host</label>
        <input className="input" value={sftp.host} onChange={(e) => setSftp({ ...sftp, host: e.target.value })} />
      </div>
      <div>
        <label className="label">Port</label>
        <input
          className="input"
          type="number"
          value={sftp.port}
          onChange={(e) => setSftp({ ...sftp, port: Number(e.target.value) })}
        />
      </div>
      <div>
        <label className="label">Username</label>
        <input className="input" value={sftp.username} onChange={(e) => setSftp({ ...sftp, username: e.target.value })} />
      </div>
      <div className="col-span-2">
        <label className="label">Password</label>
        <input
          className="input"
          type="password"
          value={sftp.password}
          onChange={(e) => setSftp({ ...sftp, password: e.target.value })}
        />
      </div>
    </div>
  )
}

function PushModal({ open, onClose, onStarted }: { open: boolean; onClose: () => void; onStarted: () => void }) {
  const [storageKey, setStorageKey] = useState('')
  const [remotePath, setRemotePath] = useState('/upload/')
  const [sftp, setSftp] = useState<SftpDetails>(BLANK_SFTP)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function start() {
    setBusy(true)
    setErr(null)
    try {
      await api('/api/transfers/sftp-push', {
        method: 'POST',
        body: { storageKey, remotePath, sftp },
      })
      onStarted()
      onClose()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to start')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Push a stored object to a partner" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Storage key (from Ad-hoc Send upload)</label>
          <input className="input" value={storageKey} onChange={(e) => setStorageKey(e.target.value)} />
        </div>
        <div>
          <label className="label">Remote path</label>
          <input className="input" value={remotePath} onChange={(e) => setRemotePath(e.target.value)} />
        </div>
        <SftpFields sftp={sftp} setSftp={setSftp} />
        <button className="btn-primary w-full" disabled={busy} onClick={start}>
          {busy ? <Spinner className="text-white" /> : 'Start durable transfer'}
        </button>
      </div>
    </Modal>
  )
}

function PullModal({ open, onClose, onStarted }: { open: boolean; onClose: () => void; onStarted: () => void }) {
  const [remotePath, setRemotePath] = useState('/upload/')
  const [sftp, setSftp] = useState<SftpDetails>(BLANK_SFTP)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function start() {
    setBusy(true)
    setErr(null)
    try {
      await api('/api/transfers/sftp-pull', { method: 'POST', body: { remotePath, sftp } })
      onStarted()
      onClose()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to start')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Pull a partner's file into storage" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Remote path</label>
          <input className="input" value={remotePath} onChange={(e) => setRemotePath(e.target.value)} />
        </div>
        <SftpFields sftp={sftp} setSftp={setSftp} />
        <button className="btn-primary w-full" disabled={busy} onClick={start}>
          {busy ? <Spinner className="text-white" /> : 'Start durable transfer'}
        </button>
      </div>
    </Modal>
  )
}

function As2SendModal({ open, onClose, onStarted }: { open: boolean; onClose: () => void; onStarted: () => void }) {
  const { data } = usePoll<As2Partner[]>(() => api('/api/as2-partners'), 0)
  const as2Partners = data ?? []
  const [storageKey, setStorageKey] = useState('')
  const [as2PartnerId, setAs2PartnerId] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function start() {
    setBusy(true)
    setErr(null)
    try {
      await api('/api/transfers/as2-send', { method: 'POST', body: { storageKey, as2PartnerId } })
      onStarted()
      onClose()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to start')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Send a stored object via AS2" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        {as2Partners.length === 0 ? (
          <EmptyState>No AS2 partners yet — add one on the Partners page first.</EmptyState>
        ) : (
          <>
            <div>
              <label className="label">Storage key (from Ad-hoc Send upload)</label>
              <input className="input" value={storageKey} onChange={(e) => setStorageKey(e.target.value)} />
            </div>
            <div>
              <label className="label">AS2 partner</label>
              <select className="input" value={as2PartnerId} onChange={(e) => setAs2PartnerId(e.target.value)}>
                <option value="">Select a partner...</option>
                {as2Partners.map((p) => (
                  <option key={p.id} value={p.id}>{p.name} ({p.partnerAs2Id})</option>
                ))}
              </select>
            </div>
            <button className="btn-primary w-full" disabled={busy || !storageKey || !as2PartnerId} onClick={start}>
              {busy ? <Spinner className="text-white" /> : 'Sign, encrypt & send'}
            </button>
          </>
        )}
      </div>
    </Modal>
  )
}

function DetailDrawer({ transfer, onClose }: { transfer: Transfer | null; onClose: () => void }) {
  if (!transfer) return null
  const rows: [string, string][] = [
    ['Status', transfer.status],
    ['Direction', transfer.direction],
    ['Source', transfer.sourceRef],
    ['Destination', transfer.destRef || '—'],
    ['Bytes', formatBytes(transfer.bytesTransferred)],
    ['SHA-256', transfer.checksumSha256 || '—'],
    ['Attempts', String(transfer.attempts)],
    ['Created by', transfer.createdBy || '—'],
    ['Created', formatDateTime(transfer.createdAt)],
    ['Started', formatDateTime(transfer.startedAt)],
    ['Completed', formatDateTime(transfer.completedAt)],
    ['Error', transfer.errorMessage || '—'],
  ]
  return (
    <Modal open={!!transfer} title={`Transfer · ${transfer.filename}`} onClose={onClose}>
      <dl className="space-y-2 text-sm">
        {rows.map(([k, v]) => (
          <div key={k} className="flex gap-4">
            <dt className="w-28 shrink-0 text-muted">{k}</dt>
            <dd className="min-w-0 break-all font-medium">{v}</dd>
          </div>
        ))}
      </dl>
    </Modal>
  )
}

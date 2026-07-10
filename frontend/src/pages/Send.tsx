import { useState } from 'react'
import { Link } from 'react-router-dom'
import { UploadCloud, CheckCircle2, AlertTriangle, FolderPlus, Send as SendIcon, Building2 } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Partner, Transfer, UploadResult } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, Spinner } from '../components/ui'
import { formatBytes } from '../lib/format'

type ItemStatus = 'queued' | 'uploading' | 'sending' | 'done' | 'failed' | 'needsFolder'

interface SendItem {
  name: string
  size: number
  status: ItemStatus
  message?: string
  transferId?: string
  storageKey?: string
  remotePath?: string
  partnerId?: string
  directory?: string
}

// Combine a drop folder and a filename into a full remote path, e.g. ("/inbound", "x.pdf") -> "/inbound/x.pdf".
function joinRemote(dir: string, name: string): string {
  let d = (dir || '').trim()
  if (!d) return '/' + name
  if (!d.startsWith('/')) d = '/' + d
  if (d.endsWith('/')) d = d.slice(0, -1)
  return d + '/' + name
}

// Poll a single transfer until it finishes (or we give up and let the Transfers page take over).
async function pollTransfer(id: string): Promise<Transfer> {
  let last: Transfer | null = null
  for (let i = 0; i < 15; i++) {
    last = await api<Transfer>(`/api/transfers/${id}`)
    if (last.status === 'SUCCEEDED' || last.status === 'FAILED') return last
    await new Promise((r) => setTimeout(r, 1500))
  }
  return last as Transfer
}

export default function Send() {
  const { data: partners, error: loadErr } = usePoll<Partner[]>(() => api('/api/partners'), 0)
  const [partnerId, setPartnerId] = useState('')
  const [folder, setFolder] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [items, setItems] = useState<SendItem[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const list = partners ?? []
  const partner = list.find((p) => p.id === partnerId)

  function selectPartner(id: string) {
    setPartnerId(id)
    const p = list.find((x) => x.id === id)
    setFolder(p?.remoteDirectory ?? '')
  }

  function patch(i: number, p: Partial<SendItem>) {
    setItems((prev) => prev.map((it, idx) => (idx === i ? { ...it, ...p } : it)))
  }

  // Given a transfer we started, wait for the outcome and update the row.
  async function settle(i: number, t: Transfer) {
    patch(i, { transferId: t.id })
    const final = await pollTransfer(t.id)
    if (final.status === 'SUCCEEDED') {
      patch(i, { status: 'done', message: `delivered to ${final.destRef}` })
    } else if (final.status === 'FAILED') {
      patch(i, { status: 'failed', message: final.errorMessage || 'Delivery failed' })
    } else {
      patch(i, { status: 'sending', message: 'Still processing — see Transfers' })
    }
  }

  async function send() {
    setConfirmOpen(false)
    setBusy(true)
    setErr(null)
    const pid = partnerId
    setItems(files.map((f) => ({ name: f.name, size: f.size, status: 'queued' })))

    for (let i = 0; i < files.length; i++) {
      const f = files[i]
      const remotePath = joinRemote(folder, f.name)
      try {
        patch(i, { status: 'uploading' })
        const form = new FormData()
        form.append('file', f)
        const up = await api<UploadResult>('/api/files', { method: 'POST', body: form, raw: true })
        patch(i, { storageKey: up.key, remotePath, partnerId: pid })

        patch(i, { status: 'sending' })
        // createRemoteDir is intentionally false: if the folder is missing we want to be told,
        // not to create it silently.
        const t = await api<Transfer>('/api/transfers/push', {
          method: 'POST',
          body: { storageKey: up.key, partnerId: pid, remotePath, createRemoteDir: false },
        })
        await settle(i, t)
      } catch (e) {
        if (e instanceof ApiError && e.status === 409 && e.data?.error === 'remote_directory_missing') {
          patch(i, {
            status: 'needsFolder',
            directory: e.data.directory,
            message: `Folder "${e.data.directory}" does not exist on the partner`,
          })
        } else {
          patch(i, { status: 'failed', message: e instanceof Error ? e.message : 'Failed' })
        }
      }
    }

    setBusy(false)
    setFiles([])
  }

  // User explicitly approved creating the missing folder for this one file.
  async function createFolderAndSend(i: number) {
    const it = items[i]
    if (!it.storageKey || !it.remotePath || !it.partnerId) return
    patch(i, { status: 'sending', message: `Creating ${it.directory} and sending…` })
    try {
      const t = await api<Transfer>('/api/transfers/push', {
        method: 'POST',
        body: {
          storageKey: it.storageKey,
          partnerId: it.partnerId,
          remotePath: it.remotePath,
          createRemoteDir: true,
        },
      })
      await settle(i, t)
    } catch (e) {
      patch(i, { status: 'failed', message: e instanceof Error ? e.message : 'Failed' })
    }
  }

  const delivered = items.filter((it) => it.status === 'done').length
  const allSettled =
    items.length > 0 && !busy && items.every((it) => it.status === 'done' || it.status === 'failed')

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Send files to a partner</h1>
        <p className="text-sm text-muted">
          Choose a partner, pick one or more files, confirm the destination, and send. Missing
          folders are never created without your say-so.
        </p>
      </div>

      <ErrorBanner message={err || loadErr} />

      <Card>
        {list.length === 0 ? (
          <EmptyState>
            You have no partners yet. Add one on the{' '}
            <Link to="/partners" className="text-brand">Partners</Link> page first.
          </EmptyState>
        ) : (
          <div className="space-y-4">
            <div>
              <label className="label">Partner</label>
              <select className="input" value={partnerId} onChange={(e) => selectPartner(e.target.value)}>
                <option value="">— Select a partner —</option>
                {list.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.host}:{p.port})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="label">Destination folder on partner</label>
              <input
                className="input"
                value={folder}
                onChange={(e) => setFolder(e.target.value)}
                placeholder="/inbound (leave blank for the partner's root)"
                disabled={!partnerId}
              />
              <p className="mt-1 text-xs text-muted">
                Pre-filled from the partner's default. If the folder doesn't exist, you'll be asked
                before it's created.
              </p>
            </div>

            <label className="flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-200 py-10 text-center hover:border-brand dark:border-slate-700">
              <UploadCloud className="mb-2 text-brand" size={34} />
              <span className="text-sm font-medium">
                {files.length > 0
                  ? `${files.length} file${files.length > 1 ? 's' : ''} selected`
                  : 'Click to choose one or more files'}
              </span>
              <span className="mt-1 text-xs text-muted">
                {files.length > 0 ? files.map((f) => f.name).join(', ') : 'You can select several at once'}
              </span>
              <input
                type="file"
                multiple
                className="hidden"
                onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
              />
            </label>

            <button
              className="btn-primary w-full"
              disabled={busy || !partnerId || files.length === 0}
              onClick={() => setConfirmOpen(true)}
            >
              {busy ? (
                <Spinner className="text-white" />
              ) : (
                <>
                  <SendIcon size={16} />
                  Review &amp; send {files.length || ''} file{files.length === 1 ? '' : 's'}
                </>
              )}
            </button>
          </div>
        )}
      </Card>

      <Modal open={confirmOpen} title="Confirm delivery" onClose={() => setConfirmOpen(false)}>
        <div className="space-y-4">
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Partner</dt>
              <dd className="text-right font-medium">
                {partner?.name}
                <div className="text-xs text-muted">{partner?.host}:{partner?.port}</div>
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Destination folder</dt>
              <dd className="font-medium">{folder.trim() || 'root (/)'}</dd>
            </div>
          </dl>
          <div>
            <div className="mb-1 text-xs uppercase tracking-wide text-muted">
              {files.length} file{files.length === 1 ? '' : 's'} will be delivered as:
            </div>
            <div className="max-h-48 space-y-1 overflow-y-auto rounded-lg border border-slate-100 p-2 dark:border-slate-800">
              {files.map((f, i) => (
                <div key={i} className="flex items-center justify-between gap-3 text-sm">
                  <span className="truncate text-muted">{f.name}</span>
                  <span className="shrink-0 font-mono text-xs">{joinRemote(folder, f.name)}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="flex gap-2">
            <button className="btn-ghost flex-1" onClick={() => setConfirmOpen(false)}>
              Cancel
            </button>
            <button className="btn-primary flex-1" onClick={send}>
              <SendIcon size={16} /> Confirm &amp; send
            </button>
          </div>
        </div>
      </Modal>

      {items.length > 0 && (
        <Card>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold">
              Delivery progress
              {allSettled && (
                <span className="ml-2 text-sm font-normal text-success">
                  {delivered}/{items.length} delivered
                </span>
              )}
            </h2>
            <Link to="/transfers" className="text-sm font-medium text-brand hover:underline">
              Track in Transfers
            </Link>
          </div>
          <div className="space-y-2">
            {items.map((it, i) => (
              <div
                key={i}
                className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 px-4 py-3 dark:border-slate-800"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 font-medium">
                    <StatusIcon status={it.status} />
                    <span className="truncate">{it.name}</span>
                  </div>
                  <div className="mt-0.5 text-xs text-muted">
                    {formatBytes(it.size)}
                    {it.message ? ` · ${it.message}` : ''}
                  </div>
                </div>
                {it.status === 'needsFolder' ? (
                  <button className="btn-primary shrink-0 !py-1.5 text-xs" onClick={() => createFolderAndSend(i)}>
                    <FolderPlus size={14} /> Create folder &amp; send
                  </button>
                ) : (
                  <span className="shrink-0 text-xs font-medium text-muted">{label(it.status)}</span>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}

function StatusIcon({ status }: { status: ItemStatus }) {
  if (status === 'done') return <CheckCircle2 className="text-success" size={16} />
  if (status === 'failed') return <AlertTriangle className="text-danger" size={16} />
  if (status === 'needsFolder') return <AlertTriangle className="text-amber-500" size={16} />
  if (status === 'uploading' || status === 'sending') return <Spinner />
  return <Building2 className="text-muted" size={16} />
}

function label(status: ItemStatus): string {
  switch (status) {
    case 'queued':
      return 'Queued'
    case 'uploading':
      return 'Uploading…'
    case 'sending':
      return 'Sending…'
    case 'done':
      return 'Delivered'
    case 'failed':
      return 'Failed'
    case 'needsFolder':
      return 'Folder missing'
    default:
      return ''
  }
}

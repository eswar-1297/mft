import { useState } from 'react'
import { Cloud, Plus, Trash2, Database, CheckCircle2 } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Connector } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, Spinner } from '../components/ui'

// The full target set; only S3 is wired end-to-end today (others show as "coming soon").
const TYPES = [
  { value: 'S3', label: 'Amazon S3 / compatible', ready: true },
  { value: 'AZURE_BLOB', label: 'Azure Blob Storage', ready: false },
  { value: 'GOOGLE_DRIVE', label: 'Google Drive', ready: false },
  { value: 'SHAREPOINT', label: 'SharePoint', ready: false },
  { value: 'BOX', label: 'Box', ready: false },
]

export default function Connectors() {
  const { data, error, refresh } = usePoll<Connector[]>(() => api('/api/connectors'), 0)
  const [addOpen, setAddOpen] = useState(false)
  const [testing, setTesting] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail'>>({})
  const connectors = data ?? []

  async function test(c: Connector) {
    setTesting(c.id)
    try {
      await api(`/api/connectors/${c.id}/test`, { method: 'POST' })
      setTestResult((r) => ({ ...r, [c.id]: 'ok' }))
    } catch {
      setTestResult((r) => ({ ...r, [c.id]: 'fail' }))
    } finally {
      setTesting(null)
    }
  }

  async function remove(c: Connector) {
    if (!confirm(`Delete connector "${c.name}"?`)) return
    await api(`/api/connectors/${c.id}`, { method: 'DELETE' })
    refresh()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Connectors</h1>
          <p className="text-sm text-muted">
            Cloud endpoints as first-class transfer sources and destinations. Credentials stored
            encrypted (AES-256-GCM).
          </p>
        </div>
        <button className="btn-primary" onClick={() => setAddOpen(true)}>
          <Plus size={16} /> Add connector
        </button>
      </div>

      <ErrorBanner message={error} />

      {connectors.length === 0 ? (
        <Card><EmptyState>No connectors yet. Add an S3 endpoint to get started.</EmptyState></Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {connectors.map((c) => (
            <Card key={c.id}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-lightblue text-brand">
                    {c.type === 'S3' ? <Database size={20} /> : <Cloud size={20} />}
                  </div>
                  <div>
                    <div className="font-semibold">{c.name}</div>
                    <div className="text-xs text-muted">{c.type}</div>
                  </div>
                </div>
                <button className="text-muted hover:text-danger" onClick={() => remove(c)}><Trash2 size={16} /></button>
              </div>
              <dl className="mt-4 space-y-1 text-sm">
                <div className="flex justify-between"><dt className="text-muted">Bucket</dt><dd className="font-medium">{c.bucket}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Endpoint</dt><dd className="truncate font-medium">{c.endpoint || 'AWS'}</dd></div>
              </dl>
              <div className="mt-3 flex items-center gap-2">
                <button className="btn-ghost !py-1 text-xs" disabled={testing === c.id} onClick={() => test(c)}>
                  {testing === c.id ? <Spinner /> : 'Test connection'}
                </button>
                {testResult[c.id] === 'ok' && <span className="flex items-center gap-1 text-xs text-success"><CheckCircle2 size={13} /> reachable</span>}
                {testResult[c.id] === 'fail' && <span className="text-xs text-danger">failed</span>}
              </div>
            </Card>
          ))}
        </div>
      )}

      <AddConnectorModal open={addOpen} onClose={() => setAddOpen(false)} onSaved={refresh} />
    </div>
  )
}

function AddConnectorModal({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState('')
  const [type, setType] = useState('S3')
  const [endpoint, setEndpoint] = useState('')
  const [region, setRegion] = useState('us-east-1')
  const [bucket, setBucket] = useState('')
  const [accessKey, setAccessKey] = useState('')
  const [secretKey, setSecretKey] = useState('')
  const [pathStyle, setPathStyle] = useState(true)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function save() {
    setBusy(true); setErr(null)
    try {
      await api('/api/connectors', {
        method: 'POST',
        body: { name, type, endpoint, region, bucket, accessKey, secretKey, pathStyle },
      })
      onSaved(); onClose()
      setName(''); setBucket(''); setAccessKey(''); setSecretKey('')
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Add connector" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Name</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Partner S3 Dropbox" />
        </div>
        <div>
          <label className="label">Type</label>
          <select className="input" value={type} onChange={(e) => setType(e.target.value)}>
            {TYPES.map((t) => (
              <option key={t.value} value={t.value} disabled={!t.ready}>
                {t.label}{t.ready ? '' : ' (coming soon)'}
              </option>
            ))}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="label">Endpoint (blank = AWS S3)</label>
            <input className="input" value={endpoint} onChange={(e) => setEndpoint(e.target.value)} placeholder="https://s3.amazonaws.com or MinIO URL" />
          </div>
          <div>
            <label className="label">Region</label>
            <input className="input" value={region} onChange={(e) => setRegion(e.target.value)} />
          </div>
          <div>
            <label className="label">Bucket</label>
            <input className="input" value={bucket} onChange={(e) => setBucket(e.target.value)} />
          </div>
          <div>
            <label className="label">Access key</label>
            <input className="input" value={accessKey} onChange={(e) => setAccessKey(e.target.value)} />
          </div>
          <div>
            <label className="label">Secret key (encrypted)</label>
            <input className="input" type="password" value={secretKey} onChange={(e) => setSecretKey(e.target.value)} />
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={pathStyle} onChange={(e) => setPathStyle(e.target.checked)} />
          Path-style access (on for MinIO/most S3-compatibles; off for AWS)
        </label>
        <button className="btn-primary w-full" disabled={busy || !name || !bucket} onClick={save}>
          {busy ? <Spinner className="text-white" /> : 'Save connector'}
        </button>
      </div>
    </Modal>
  )
}

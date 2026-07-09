import { useState } from 'react'
import { Building2, Plus, Trash2, KeyRound } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Partner } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, Spinner } from '../components/ui'

export default function Partners() {
  const { data, error, refresh } = usePoll<Partner[]>(() => api('/api/partners'), 0)
  const [addOpen, setAddOpen] = useState(false)
  const partners = data ?? []

  async function remove(p: Partner) {
    if (!confirm(`Delete partner "${p.name}"? This cannot be undone.`)) return
    await api(`/api/partners/${p.id}`, { method: 'DELETE' })
    refresh()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Partners</h1>
          <p className="text-sm text-muted">
            Trading partners and their connection profiles. Credentials are stored encrypted (AES-256-GCM).
          </p>
        </div>
        <button className="btn-primary" onClick={() => setAddOpen(true)}>
          <Plus size={16} /> Add partner
        </button>
      </div>

      <ErrorBanner message={error} />

      {partners.length === 0 ? (
        <Card>
          <EmptyState>No partners yet. Add one to start exchanging files.</EmptyState>
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {partners.map((p) => (
            <Card key={p.id}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-lightblue text-brand">
                    <Building2 size={20} />
                  </div>
                  <div>
                    <div className="font-semibold">{p.name}</div>
                    <div className="text-xs text-muted">{p.protocol}</div>
                  </div>
                </div>
                <button className="text-muted hover:text-danger" onClick={() => remove(p)} title="Delete">
                  <Trash2 size={16} />
                </button>
              </div>
              <dl className="mt-4 space-y-1 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted">Endpoint</dt>
                  <dd className="font-medium">{p.host}:{p.port}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted">Username</dt>
                  <dd className="font-medium">{p.username}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted">Credential</dt>
                  <dd className="flex items-center gap-1 font-medium">
                    <KeyRound size={13} className={p.hasStoredSecret ? 'text-success' : 'text-muted'} />
                    {p.hasStoredSecret ? 'Stored (encrypted)' : 'None'}
                  </dd>
                </div>
              </dl>
            </Card>
          ))}
        </div>
      )}

      <AddPartnerModal open={addOpen} onClose={() => setAddOpen(false)} onSaved={refresh} />
    </div>
  )
}

function AddPartnerModal({
  open,
  onClose,
  onSaved,
}: {
  open: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState('')
  const [host, setHost] = useState('')
  const [port, setPort] = useState(22)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function save() {
    setBusy(true)
    setErr(null)
    try {
      await api('/api/partners', { method: 'POST', body: { name, host, port, username, password } })
      onSaved()
      onClose()
      setName(''); setHost(''); setPort(22); setUsername(''); setPassword('')
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to save partner')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Add partner" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Partner name</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="HDFC Bank" />
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div className="col-span-2">
            <label className="label">SFTP host</label>
            <input className="input" value={host} onChange={(e) => setHost(e.target.value)} />
          </div>
          <div>
            <label className="label">Port</label>
            <input className="input" type="number" value={port} onChange={(e) => setPort(Number(e.target.value))} />
          </div>
        </div>
        <div>
          <label className="label">Username</label>
          <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div>
          <label className="label">Password (stored encrypted)</label>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn-primary w-full" disabled={busy || !name || !host || !username} onClick={save}>
          {busy ? <Spinner className="text-white" /> : 'Save partner'}
        </button>
      </div>
    </Modal>
  )
}

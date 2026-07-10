import { useEffect, useState } from 'react'
import { Building2, Plus, Trash2, KeyRound, Pencil, Folder } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Partner } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, Spinner } from '../components/ui'

export default function Partners() {
  const { data, error, refresh } = usePoll<Partner[]>(() => api('/api/partners'), 0)
  const [addOpen, setAddOpen] = useState(false)
  const [editing, setEditing] = useState<Partner | null>(null)
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
                <div className="flex items-center gap-1">
                  <button className="text-muted hover:text-brand" onClick={() => setEditing(p)} title="Edit">
                    <Pencil size={16} />
                  </button>
                  <button className="text-muted hover:text-danger" onClick={() => remove(p)} title="Delete">
                    <Trash2 size={16} />
                  </button>
                </div>
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
                  <dt className="text-muted">Drop folder</dt>
                  <dd className="flex items-center gap-1 font-medium">
                    <Folder size={13} className="text-muted" />
                    {p.remoteDirectory || 'root (/)'}
                  </dd>
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

      <PartnerFormModal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onSaved={refresh}
      />
      <PartnerFormModal
        open={editing !== null}
        partner={editing}
        onClose={() => setEditing(null)}
        onSaved={refresh}
      />
    </div>
  )
}

function PartnerFormModal({
  open,
  partner,
  onClose,
  onSaved,
}: {
  open: boolean
  partner?: Partner | null
  onClose: () => void
  onSaved: () => void
}) {
  const isEdit = !!partner
  const [name, setName] = useState('')
  const [host, setHost] = useState('')
  const [port, setPort] = useState(22)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remoteDirectory, setRemoteDirectory] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  // Prefill when editing (or reset when the modal opens for a new partner).
  useEffect(() => {
    if (!open) return
    setName(partner?.name ?? '')
    setHost(partner?.host ?? '')
    setPort(partner?.port ?? 22)
    setUsername(partner?.username ?? '')
    setRemoteDirectory(partner?.remoteDirectory ?? '')
    setPassword('')
    setErr(null)
  }, [open, partner])

  async function save() {
    setBusy(true)
    setErr(null)
    try {
      const body = { name, host, port, username, password, remoteDirectory }
      if (isEdit) {
        await api(`/api/partners/${partner!.id}`, { method: 'PUT', body })
      } else {
        await api('/api/partners', { method: 'POST', body })
      }
      onSaved()
      onClose()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to save partner')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title={isEdit ? 'Edit partner' : 'Add partner'} onClose={onClose}>
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
          <label className="label">
            Password {isEdit && <span className="text-muted">(leave blank to keep current)</span>}
          </label>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <div>
          <label className="label">Default drop folder <span className="text-muted">(optional)</span></label>
          <input
            className="input"
            value={remoteDirectory}
            onChange={(e) => setRemoteDirectory(e.target.value)}
            placeholder="/inbound"
          />
          <p className="mt-1 text-xs text-muted">
            Where files land on the partner by default, e.g. <code>/inbound</code>. Leave blank for the root.
          </p>
        </div>
        <button
          className="btn-primary w-full"
          disabled={busy || !name || !host || !username}
          onClick={save}
        >
          {busy ? <Spinner className="text-white" /> : isEdit ? 'Save changes' : 'Save partner'}
        </button>
      </div>
    </Modal>
  )
}

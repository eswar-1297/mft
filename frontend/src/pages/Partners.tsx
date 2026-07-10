import { useState } from 'react'
import { Building2, Plus, Trash2, KeyRound, ShieldCheck } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Partner, As2Partner, As2Identity } from '../lib/types'
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

      <As2PartnersSection />
    </div>
  )
}

function As2PartnersSection() {
  const { data, error, refresh } = usePoll<As2Partner[]>(() => api('/api/as2-partners'), 0)
  const { data: identity } = usePoll<As2Identity>(() => api('/api/as2-partners/identity'), 0)
  const [addOpen, setAddOpen] = useState(false)
  const as2Partners = data ?? []

  async function remove(p: As2Partner) {
    if (!confirm(`Delete AS2 partner "${p.name}"? This cannot be undone.`)) return
    await api(`/api/as2-partners/${p.id}`, { method: 'DELETE' })
    refresh()
  }

  return (
    <div className="space-y-4 border-t pt-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">AS2 Partners</h2>
          <p className="text-sm text-muted">
            Sign, encrypt, and exchange EDI files over AS2 (RFC 4130). Certificates are exchanged
            out-of-band and pinned directly, the same trust model as SFTP host keys.
          </p>
        </div>
        <button className="btn-primary" onClick={() => setAddOpen(true)}>
          <Plus size={16} /> Add AS2 partner
        </button>
      </div>

      {identity && (
        <Card>
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-lightblue text-brand">
              <ShieldCheck size={20} />
            </div>
            <div className="min-w-0 flex-1">
              <div className="font-semibold">Your AS2 identity</div>
              <p className="text-xs text-muted">Share this ID and certificate with a real partner so they can trust and reach you.</p>
              <dl className="mt-2 space-y-1 text-sm">
                <div className="flex justify-between"><dt className="text-muted">AS2-From ID</dt><dd className="font-medium">{identity.as2Id}</dd></div>
              </dl>
              <details className="mt-2">
                <summary className="cursor-pointer text-xs text-brand">Show certificate (PEM)</summary>
                <pre className="mt-1 max-h-40 overflow-auto rounded bg-lightgray p-2 text-xs">{identity.certificatePem}</pre>
              </details>
            </div>
          </div>
        </Card>
      )}

      <ErrorBanner message={error} />

      {as2Partners.length === 0 ? (
        <Card><EmptyState>No AS2 partners yet. Add one to exchange EDI files.</EmptyState></Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {as2Partners.map((p) => (
            <Card key={p.id}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-lightblue text-brand">
                    <ShieldCheck size={20} />
                  </div>
                  <div>
                    <div className="font-semibold">{p.name}</div>
                    <div className="text-xs text-muted">AS2</div>
                  </div>
                </div>
                <button className="text-muted hover:text-danger" onClick={() => remove(p)} title="Delete">
                  <Trash2 size={16} />
                </button>
              </div>
              <dl className="mt-4 space-y-1 text-sm">
                <div className="flex justify-between"><dt className="text-muted">Partner AS2 ID</dt><dd className="font-medium">{p.partnerAs2Id}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Inbound URL</dt><dd className="truncate font-medium">{p.inboundUrl}</dd></div>
              </dl>
            </Card>
          ))}
        </div>
      )}

      <AddAs2PartnerModal open={addOpen} onClose={() => setAddOpen(false)} onSaved={refresh} />
    </div>
  )
}

function AddAs2PartnerModal({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState('')
  const [partnerAs2Id, setPartnerAs2Id] = useState('')
  const [partnerCertificatePem, setPartnerCertificatePem] = useState('')
  const [inboundUrl, setInboundUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function save() {
    setBusy(true); setErr(null)
    try {
      await api('/api/as2-partners', {
        method: 'POST',
        body: { name, partnerAs2Id, partnerCertificatePem, inboundUrl },
      })
      onSaved(); onClose()
      setName(''); setPartnerAs2Id(''); setPartnerCertificatePem(''); setInboundUrl('')
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to save AS2 partner')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Add AS2 partner" onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Partner name</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Insurance" />
        </div>
        <div>
          <label className="label">Partner AS2-From ID</label>
          <input className="input" value={partnerAs2Id} onChange={(e) => setPartnerAs2Id(e.target.value)} placeholder="ACME-INSURANCE" />
        </div>
        <div>
          <label className="label">Inbound URL (where we send them files)</label>
          <input className="input" value={inboundUrl} onChange={(e) => setInboundUrl(e.target.value)} placeholder="https://partner.example.com/as2/inbound" />
        </div>
        <div>
          <label className="label">Partner certificate (PEM, public — not secret)</label>
          <textarea className="input h-28 font-mono text-xs" value={partnerCertificatePem}
                    onChange={(e) => setPartnerCertificatePem(e.target.value)}
                    placeholder="-----BEGIN CERTIFICATE-----..." />
        </div>
        <button className="btn-primary w-full" disabled={busy || !name || !partnerAs2Id || !inboundUrl || !partnerCertificatePem} onClick={save}>
          {busy ? <Spinner className="text-white" /> : 'Save AS2 partner'}
        </button>
      </div>
    </Modal>
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

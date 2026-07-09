import { useState } from 'react'
import {
  Plus, Play, Trash2, ArrowDown, ArrowUp, X, Clock,
  Download, Lock, Unlock, CheckCircle2, Upload, Archive, Bell, CircleCheck, CircleX, Circle,
} from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { Connector, Partner, StepResult, StepType, Workflow, WorkflowRun, WorkflowStep } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Modal, Spinner, StatusBadge } from '../components/ui'
import { formatDateTime } from '../lib/format'

const STEP_META: Record<StepType, { label: string; icon: typeof Download; hint: string }> = {
  PICKUP: { label: 'Pickup', icon: Download, hint: 'Bring a file in (storage key or partner SFTP)' },
  PGP_ENCRYPT: { label: 'PGP Encrypt', icon: Lock, hint: 'Encrypt with a passphrase (OpenPGP)' },
  PGP_DECRYPT: { label: 'PGP Decrypt', icon: Unlock, hint: 'Decrypt a PGP file' },
  VALIDATE: { label: 'Validate', icon: CheckCircle2, hint: 'Check size constraints' },
  SEND: { label: 'Send', icon: Upload, hint: 'Deliver to a partner over SFTP' },
  ARCHIVE: { label: 'Archive', icon: Archive, hint: 'Copy to the archive namespace' },
  NOTIFY: { label: 'Notify', icon: Bell, hint: 'Record a notification + audit event' },
}
const PALETTE: StepType[] = ['PICKUP', 'PGP_ENCRYPT', 'PGP_DECRYPT', 'VALIDATE', 'SEND', 'ARCHIVE', 'NOTIFY']

export default function Workflows() {
  const { data: workflows, error, refresh } = usePoll<Workflow[]>(() => api('/api/workflows'), 4000)
  const { data: runsPage, refresh: refreshRuns } = usePoll<{ content: WorkflowRun[] }>(
    () => api('/api/workflows/runs?size=20'), 3000,
  )
  const [builderOpen, setBuilderOpen] = useState(false)
  const [busyRun, setBusyRun] = useState<string | null>(null)
  const [scheduling, setScheduling] = useState<Workflow | null>(null)

  const list = workflows ?? []
  const runs = runsPage?.content ?? []

  async function runWorkflow(w: Workflow) {
    setBusyRun(w.id)
    try {
      await api(`/api/workflows/${w.id}/run`, { method: 'POST' })
      refreshRuns()
    } finally {
      setBusyRun(null)
    }
  }

  async function remove(w: Workflow) {
    if (!confirm(`Delete workflow "${w.name}"?`)) return
    await api(`/api/workflows/${w.id}`, { method: 'DELETE' })
    refresh()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Workflows</h1>
          <p className="text-sm text-muted">Durable, multi-step pipelines. Each run survives worker restarts.</p>
        </div>
        <button className="btn-primary" onClick={() => setBuilderOpen(true)}>
          <Plus size={16} /> Build workflow
        </button>
      </div>

      <ErrorBanner message={error} />

      {list.length === 0 ? (
        <Card><EmptyState>No workflows yet. Build your first pipeline.</EmptyState></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {list.map((w) => (
            <Card key={w.id}>
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-semibold">{w.name}</div>
                  <div className="flex items-center gap-1 text-xs text-muted">
                    {w.steps.length} steps
                    {w.lastStatus && <> · last run <StatusBadge status={w.lastStatus} /></>}
                    {w.cronSchedule && (
                      <span className="ml-1 inline-flex items-center gap-1 rounded-full bg-lightblue px-2 py-0.5 text-brand">
                        <Clock size={11} /> {w.cronSchedule}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex gap-1">
                  <button className="btn-ghost !px-2 !py-1.5 text-xs" onClick={() => setScheduling(w)} title="Schedule">
                    <Clock size={15} />
                  </button>
                  <button className="btn-primary !px-3 !py-1.5 text-xs" disabled={busyRun === w.id} onClick={() => runWorkflow(w)}>
                    {busyRun === w.id ? <Spinner className="text-white" /> : <><Play size={13} /> Run</>}
                  </button>
                  <button className="text-muted hover:text-danger" onClick={() => remove(w)}><Trash2 size={16} /></button>
                </div>
              </div>
              <PipelineStrip steps={w.steps} />
            </Card>
          ))}
        </div>
      )}

      <Card>
        <h2 className="mb-3 font-semibold">Recent runs</h2>
        {runs.length === 0 ? (
          <EmptyState>No runs yet.</EmptyState>
        ) : (
          <div className="space-y-3">
            {runs.map((r) => <RunRow key={r.id} run={r} />)}
          </div>
        )}
      </Card>

      <BuilderModal
        open={builderOpen}
        onClose={() => setBuilderOpen(false)}
        onSaved={() => { setBuilderOpen(false); refresh() }}
      />
      <ScheduleModal
        workflow={scheduling}
        onClose={() => setScheduling(null)}
        onSaved={() => { setScheduling(null); refresh() }}
      />
    </div>
  )
}

function PipelineStrip({ steps }: { steps: WorkflowStep[] }) {
  return (
    <div className="mt-4 flex flex-wrap items-center gap-1">
      {steps.map((s, i) => {
        const Icon = STEP_META[s.type].icon
        return (
          <span key={i} className="flex items-center gap-1">
            <span className="inline-flex items-center gap-1 rounded-md bg-lightblue px-2 py-1 text-xs font-medium text-brand">
              <Icon size={12} /> {STEP_META[s.type].label}
            </span>
            {i < steps.length - 1 && <span className="text-muted">→</span>}
          </span>
        )
      })}
    </div>
  )
}

function RunRow({ run }: { run: WorkflowRun }) {
  const results: StepResult[] = run.stepResultsJson ? JSON.parse(run.stepResultsJson) : []
  return (
    <div className="rounded-lg border border-slate-100 p-3 dark:border-slate-800">
      <div className="flex items-center justify-between">
        <div className="font-medium">{run.workflowName}</div>
        <div className="flex items-center gap-2">
          <StatusBadge status={run.status} />
          <span className="text-xs text-muted">{formatDateTime(run.startedAt)}</span>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {results.map((s) => (
          <span key={s.index} className="inline-flex items-center gap-1 text-xs" title={s.detail}>
            {s.status === 'SUCCEEDED' ? (
              <CircleCheck size={13} className="text-success" />
            ) : (
              <CircleX size={13} className="text-danger" />
            )}
            {STEP_META[s.type].label}
          </span>
        ))}
        {run.status === 'RUNNING' && results.length === 0 && (
          <span className="inline-flex items-center gap-1 text-xs text-muted"><Circle size={13} /> starting…</span>
        )}
      </div>
      {run.errorMessage && <div className="mt-1 text-xs text-danger">{run.errorMessage}</div>}
    </div>
  )
}

function BuilderModal({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState('')
  const [steps, setSteps] = useState<WorkflowStep[]>([])
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const { data: partners } = usePoll<Partner[]>(() => api('/api/partners'), 0)
  const { data: connectors } = usePoll<Connector[]>(() => api('/api/connectors'), 0)

  function addStep(type: StepType) {
    setSteps((s) => [...s, { type, config: {} }])
  }
  function move(i: number, dir: -1 | 1) {
    setSteps((s) => {
      const next = [...s]
      const j = i + dir
      if (j < 0 || j >= next.length) return s
      ;[next[i], next[j]] = [next[j], next[i]]
      return next
    })
  }
  function removeStep(i: number) {
    setSteps((s) => s.filter((_, idx) => idx !== i))
  }
  function setCfg(i: number, key: string, value: string) {
    setSteps((s) => s.map((st, idx) => (idx === i ? { ...st, config: { ...st.config, [key]: value } } : st)))
  }

  async function save() {
    setBusy(true); setErr(null)
    try {
      await api('/api/workflows', { method: 'POST', body: { name, steps } })
      onSaved()
      setName(''); setSteps([])
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="Build a workflow" onClose={onClose}>
      <div className="space-y-4">
        <ErrorBanner message={err} />
        <div>
          <label className="label">Workflow name</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Nightly claims to Star Health" />
        </div>

        <div>
          <label className="label">Add a step</label>
          <div className="flex flex-wrap gap-1">
            {PALETTE.map((t) => {
              const Icon = STEP_META[t].icon
              return (
                <button key={t} className="btn-ghost !px-2 !py-1 text-xs" title={STEP_META[t].hint} onClick={() => addStep(t)}>
                  <Icon size={13} /> {STEP_META[t].label}
                </button>
              )
            })}
          </div>
        </div>

        {steps.length > 0 && (
          <div className="space-y-2">
            {steps.map((s, i) => (
              <div key={i} className="rounded-lg border border-slate-200 p-3 dark:border-slate-700">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium">{i + 1}. {STEP_META[s.type].label}</span>
                  <div className="flex gap-1 text-muted">
                    <button onClick={() => move(i, -1)} disabled={i === 0}><ArrowUp size={14} /></button>
                    <button onClick={() => move(i, 1)} disabled={i === steps.length - 1}><ArrowDown size={14} /></button>
                    <button onClick={() => removeStep(i)} className="hover:text-danger"><X size={14} /></button>
                  </div>
                </div>
                <StepConfig step={s} index={i} setCfg={setCfg} partners={partners ?? []} connectors={connectors ?? []} />
              </div>
            ))}
          </div>
        )}

        <button className="btn-primary w-full" disabled={busy || !name || steps.length === 0} onClick={save}>
          {busy ? <Spinner className="text-white" /> : 'Save workflow'}
        </button>
      </div>
    </Modal>
  )
}

function StepConfig({
  step, index, setCfg, partners, connectors,
}: {
  step: WorkflowStep
  index: number
  setCfg: (i: number, key: string, value: string) => void
  partners: Partner[]
  connectors: Connector[]
}) {
  const c = step.config
  const partnerSelect = (
    <select className="input" value={c.partnerId || ''} onChange={(e) => setCfg(index, 'partnerId', e.target.value)}>
      <option value="">Select partner…</option>
      {partners.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.host})</option>)}
    </select>
  )
  const connectorFields = (
    <>
      <select className="input" value={c.connectorId || ''} onChange={(e) => setCfg(index, 'connectorId', e.target.value)}>
        <option value="">Select connector…</option>
        {connectors.map((cn) => <option key={cn.id} value={cn.id}>{cn.name} ({cn.type})</option>)}
      </select>
      <input className="input col-span-2" placeholder="object key, e.g. nightly/export.csv" value={c.objectKey || ''} onChange={(e) => setCfg(index, 'objectKey', e.target.value)} />
    </>
  )

  switch (step.type) {
    case 'PICKUP':
      return (
        <div className="grid grid-cols-2 gap-2">
          <select className="input" value={c.source || 'STORAGE'} onChange={(e) => setCfg(index, 'source', e.target.value)}>
            <option value="STORAGE">From storage key</option>
            <option value="SFTP">From partner SFTP</option>
            <option value="CONNECTOR">From cloud connector</option>
          </select>
          {(c.source || 'STORAGE') === 'STORAGE' && (
            <input className="input" placeholder="storage key" value={c.storageKey || ''} onChange={(e) => setCfg(index, 'storageKey', e.target.value)} />
          )}
          {c.source === 'SFTP' && (
            <>
              {partnerSelect}
              <input className="input col-span-2" placeholder="/remote/path" value={c.remotePath || ''} onChange={(e) => setCfg(index, 'remotePath', e.target.value)} />
            </>
          )}
          {c.source === 'CONNECTOR' && connectorFields}
        </div>
      )
    case 'PGP_ENCRYPT':
    case 'PGP_DECRYPT':
      return <input className="input" type="password" placeholder="passphrase" value={c.passphrase || ''} onChange={(e) => setCfg(index, 'passphrase', e.target.value)} />
    case 'VALIDATE':
      return (
        <div className="grid grid-cols-2 gap-2">
          <input className="input" placeholder="min bytes" value={c.minBytes || ''} onChange={(e) => setCfg(index, 'minBytes', e.target.value)} />
          <input className="input" placeholder="max bytes" value={c.maxBytes || ''} onChange={(e) => setCfg(index, 'maxBytes', e.target.value)} />
        </div>
      )
    case 'SEND':
      return (
        <div className="grid grid-cols-2 gap-2">
          <select className="input" value={c.dest || 'SFTP'} onChange={(e) => setCfg(index, 'dest', e.target.value)}>
            <option value="SFTP">To partner SFTP</option>
            <option value="CONNECTOR">To cloud connector</option>
            <option value="STORAGE">Keep in storage</option>
          </select>
          {(c.dest || 'SFTP') === 'SFTP' && (
            <>
              {partnerSelect}
              <input className="input col-span-2" placeholder="/remote/path" value={c.remotePath || ''} onChange={(e) => setCfg(index, 'remotePath', e.target.value)} />
            </>
          )}
          {c.dest === 'CONNECTOR' && connectorFields}
        </div>
      )
    case 'NOTIFY':
      return <input className="input" placeholder="message" value={c.message || ''} onChange={(e) => setCfg(index, 'message', e.target.value)} />
    default:
      return <div className="text-xs text-muted">No configuration needed.</div>
  }
}

const CRON_PRESETS: { label: string; cron: string }[] = [
  { label: 'Every minute (test)', cron: '* * * * *' },
  { label: 'Every 15 minutes', cron: '*/15 * * * *' },
  { label: 'Hourly', cron: '0 * * * *' },
  { label: 'Daily at 02:00 UTC', cron: '0 2 * * *' },
  { label: 'Weekdays at 06:00 UTC', cron: '0 6 * * 1-5' },
]

function ScheduleModal({
  workflow, onClose, onSaved,
}: {
  workflow: Workflow | null
  onClose: () => void
  onSaved: () => void
}) {
  const [cron, setCron] = useState('0 2 * * *')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  if (!workflow) return null

  async function save() {
    setBusy(true); setErr(null)
    try {
      await api(`/api/workflows/${workflow!.id}/schedule`, { method: 'POST', body: { cron } })
      onSaved()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to schedule')
    } finally {
      setBusy(false)
    }
  }

  async function clear() {
    setBusy(true); setErr(null)
    try {
      await api(`/api/workflows/${workflow!.id}/schedule`, { method: 'DELETE' })
      onSaved()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={!!workflow} title={`Schedule · ${workflow.name}`} onClose={onClose}>
      <div className="space-y-3">
        <ErrorBanner message={err} />
        {workflow.cronSchedule && (
          <div className="rounded-lg bg-lightblue px-3 py-2 text-sm text-brand">
            Currently scheduled: <span className="font-mono font-medium">{workflow.cronSchedule}</span>
          </div>
        )}
        <div>
          <label className="label">Preset</label>
          <select className="input" onChange={(e) => setCron(e.target.value)} value={cron}>
            {CRON_PRESETS.map((p) => <option key={p.cron} value={p.cron}>{p.label} — {p.cron}</option>)}
          </select>
        </div>
        <div>
          <label className="label">Cron expression (UTC, 5-field)</label>
          <input className="input font-mono" value={cron} onChange={(e) => setCron(e.target.value)} />
        </div>
        <div className="flex gap-2">
          <button className="btn-primary flex-1" disabled={busy} onClick={save}>
            {busy ? <Spinner className="text-white" /> : 'Schedule'}
          </button>
          {workflow.cronSchedule && (
            <button className="btn-ghost text-danger" disabled={busy} onClick={clear}>
              Remove schedule
            </button>
          )}
        </div>
      </div>
    </Modal>
  )
}

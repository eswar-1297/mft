import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, Circle, Timer, Rocket } from 'lucide-react'
import { api } from '../lib/api'
import type { Partner, Transfer, UploadResult } from '../lib/types'
import { Card, ErrorBanner, Spinner } from '../components/ui'
import { formatDuration } from '../lib/format'

type StepState = 'idle' | 'active' | 'done'

/**
 * The demo opener: connect a partner, stage a test file, and run a real transfer live — proving a
 * first working flow in minutes. The elapsed-time chip is driven by wall-clock, and every step
 * calls the real backend (partner create, upload, durable transfer with polling).
 */
export default function Onboarding() {
  const navigate = useNavigate()
  const [name, setName] = useState('Demo Partner')
  const [host, setHost] = useState('127.0.0.1')
  const [port, setPort] = useState(2222)
  const [username, setUsername] = useState('foo')
  const [password, setPassword] = useState('pass123')
  const [remotePath, setRemotePath] = useState('/upload/onboarding-test.bin')

  const [running, setRunning] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [steps, setSteps] = useState<Record<string, StepState>>({
    partner: 'idle',
    upload: 'idle',
    transfer: 'idle',
  })
  const [elapsed, setElapsed] = useState(0)
  const [done, setDone] = useState(false)
  const startRef = useRef<number>(0)
  const timerRef = useRef<number | null>(null)

  useEffect(() => () => { if (timerRef.current) window.clearInterval(timerRef.current) }, [])

  function setStep(key: string, state: StepState) {
    setSteps((s) => ({ ...s, [key]: state }))
  }

  async function run() {
    setRunning(true)
    setErr(null)
    setDone(false)
    setSteps({ partner: 'idle', upload: 'idle', transfer: 'idle' })
    startRef.current = Date.now()
    setElapsed(0)
    timerRef.current = window.setInterval(() => setElapsed((Date.now() - startRef.current) / 1000), 100)
    try {
      // 1. Create the partner (stores an encrypted credential).
      setStep('partner', 'active')
      const partner = await api<Partner>('/api/partners', {
        method: 'POST',
        body: { name: `${name} ${Date.now()}`, host, port, username, password },
      })
      setStep('partner', 'done')

      // 2. Stage a small test file in secure storage.
      setStep('upload', 'active')
      const bytes = new Uint8Array(64 * 1024)
      crypto.getRandomValues(bytes)
      const form = new FormData()
      form.append('file', new Blob([bytes]), 'onboarding-test.bin')
      const uploaded = await api<UploadResult>('/api/files', { method: 'POST', body: form, raw: true })
      setStep('upload', 'done')

      // 3. Run a real durable transfer to the partner and poll to completion.
      setStep('transfer', 'active')
      const started = await api<Transfer>('/api/transfers/push', {
        method: 'POST',
        body: { storageKey: uploaded.key, partnerId: partner.id, remotePath },
      })
      const final = await pollUntilDone(started.id)
      if (final.status !== 'SUCCEEDED') {
        throw new Error(final.errorMessage || 'Test transfer did not succeed')
      }
      setStep('transfer', 'done')
      setDone(true)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Onboarding failed')
    } finally {
      if (timerRef.current) window.clearInterval(timerRef.current)
      setRunning(false)
    }
  }

  async function pollUntilDone(id: string): Promise<Transfer> {
    for (let i = 0; i < 60; i++) {
      const t = await api<Transfer>(`/api/transfers/${id}`)
      if (t.status === 'SUCCEEDED' || t.status === 'FAILED') return t
      await new Promise((r) => setTimeout(r, 500))
    }
    throw new Error('Transfer timed out')
  }

  const STEP_LABELS: [string, string][] = [
    ['partner', 'Connect a partner (SFTP + encrypted credential)'],
    ['upload', 'Stage a test file in secure storage'],
    ['transfer', 'Run a live, durable transfer to the partner'],
  ]

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="text-center">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-brand text-white">
          <Rocket size={22} />
        </div>
        <h1 className="text-2xl font-semibold">Get a live workflow running</h1>
        <p className="text-sm text-muted">
          Connect a partner and run a real transfer — start to finish, in minutes. No services engagement.
        </p>
      </div>

      <Card>
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="label">Partner name</label>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} disabled={running} />
          </div>
          <div>
            <label className="label">SFTP host</label>
            <input className="input" value={host} onChange={(e) => setHost(e.target.value)} disabled={running} />
          </div>
          <div>
            <label className="label">Port</label>
            <input className="input" type="number" value={port} onChange={(e) => setPort(Number(e.target.value))} disabled={running} />
          </div>
          <div>
            <label className="label">Username</label>
            <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} disabled={running} />
          </div>
          <div>
            <label className="label">Password</label>
            <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} disabled={running} />
          </div>
          <div className="col-span-2">
            <label className="label">Remote path</label>
            <input className="input" value={remotePath} onChange={(e) => setRemotePath(e.target.value)} disabled={running} />
          </div>
        </div>
      </Card>

      <ErrorBanner message={err} />

      <Card>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-semibold">Progress</h2>
          <div className="flex items-center gap-2 rounded-full bg-lightblue px-3 py-1 text-sm font-medium text-brand">
            <Timer size={14} /> {formatDuration(elapsed)}
          </div>
        </div>
        <ol className="space-y-3">
          {STEP_LABELS.map(([key, label]) => (
            <li key={key} className="flex items-center gap-3">
              {steps[key] === 'done' ? (
                <CheckCircle2 className="text-success" size={20} />
              ) : steps[key] === 'active' ? (
                <Spinner className="text-brand" />
              ) : (
                <Circle className="text-muted" size={20} />
              )}
              <span className={steps[key] === 'idle' ? 'text-muted' : 'font-medium'}>{label}</span>
            </li>
          ))}
        </ol>

        {done && (
          <div className="mt-5 rounded-lg bg-green-50 px-4 py-3 text-green-800 dark:bg-green-900/20 dark:text-green-300">
            <div className="font-semibold">Workflow live in {formatDuration(elapsed)} ✅</div>
            <div className="text-sm">
              A partner is connected and a real file was delivered and integrity-checked.
            </div>
          </div>
        )}

        <div className="mt-5 flex gap-2">
          <button className="btn-primary" disabled={running} onClick={run}>
            {running ? <Spinner className="text-white" /> : done ? 'Run again' : 'Start'}
          </button>
          {done && (
            <button className="btn-ghost" onClick={() => navigate('/')}>
              Go to dashboard
            </button>
          )}
        </div>
      </Card>
    </div>
  )
}

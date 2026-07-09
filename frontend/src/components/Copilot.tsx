import { useEffect, useState } from 'react'
import { Sparkles, X, Send, FileCog, ScrollText } from 'lucide-react'
import { api } from '../lib/api'
import type { AskResponse, DraftWorkflowResponse } from '../lib/types'
import { Spinner } from './ui'

const STEP_LABEL: Record<string, string> = {
  PICKUP: 'Pickup', PGP_ENCRYPT: 'PGP Encrypt', PGP_DECRYPT: 'PGP Decrypt',
  VALIDATE: 'Validate', SEND: 'Send', ARCHIVE: 'Archive', NOTIFY: 'Notify',
}

/**
 * Docked AI Copilot: draft a workflow from natural language, or ask plain-English questions about
 * the audit trail. Advisory only — drafts are shown for review, never auto-created; audit answers
 * cite the exact records they're based on.
 */
export default function Copilot() {
  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState<'draft' | 'ask'>('draft')
  const [aiAvailable, setAiAvailable] = useState(false)

  useEffect(() => {
    api<{ aiAvailable: boolean }>('/api/copilot/status')
      .then((s) => setAiAvailable(s.aiAvailable))
      .catch(() => {})
  }, [])

  return (
    <>
      {!open && (
        <button
          className="fixed bottom-6 right-6 z-40 flex items-center gap-2 rounded-full bg-brand px-4 py-3 text-white shadow-cardhover hover:bg-brand-dark"
          onClick={() => setOpen(true)}
        >
          <Sparkles size={18} /> Copilot
        </button>
      )}
      {open && (
        <div className="fixed bottom-6 right-6 z-40 flex h-[600px] w-[420px] max-w-[calc(100vw-2rem)] flex-col rounded-xl border border-slate-200 bg-white shadow-cardhover dark:border-slate-700 dark:bg-[#141B2E]">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-800">
            <div className="flex items-center gap-2 font-semibold">
              <Sparkles size={18} className="text-brand" /> AI Copilot
              <span className={`rounded-full px-2 py-0.5 text-xs ${aiAvailable ? 'bg-green-100 text-success dark:bg-green-900/40' : 'bg-slate-100 text-muted dark:bg-slate-800'}`}>
                {aiAvailable ? 'Claude' : 'rule-based'}
              </span>
            </div>
            <button className="text-muted hover:text-ink dark:hover:text-white" onClick={() => setOpen(false)}>
              <X size={18} />
            </button>
          </div>
          <div className="flex border-b border-slate-100 text-sm dark:border-slate-800">
            <button className={`flex flex-1 items-center justify-center gap-1 py-2 ${tab === 'draft' ? 'border-b-2 border-brand font-medium text-brand' : 'text-muted'}`} onClick={() => setTab('draft')}>
              <FileCog size={15} /> Build workflow
            </button>
            <button className={`flex flex-1 items-center justify-center gap-1 py-2 ${tab === 'ask' ? 'border-b-2 border-brand font-medium text-brand' : 'text-muted'}`} onClick={() => setTab('ask')}>
              <ScrollText size={15} /> Ask audit
            </button>
          </div>
          <div className="flex-1 overflow-y-auto p-4">
            {tab === 'draft' ? <DraftTab /> : <AskTab />}
          </div>
        </div>
      )}
    </>
  )
}

function DraftTab() {
  const [prompt, setPrompt] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<DraftWorkflowResponse | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function run() {
    setBusy(true); setErr(null); setResult(null)
    try {
      setResult(await api('/api/copilot/draft-workflow', { method: 'POST', body: { prompt } }))
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted">
        Describe a transfer in plain English. The Copilot drafts a workflow — you review and save it
        (it never creates or runs anything on its own).
      </p>
      <textarea
        className="input h-24 resize-none"
        placeholder="e.g. every night at 2am encrypt /payroll and send to HDFC Bank via SFTP, then archive"
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
      />
      <button className="btn-primary w-full" disabled={busy || !prompt.trim()} onClick={run}>
        {busy ? <Spinner className="text-white" /> : <><Send size={15} /> Draft workflow</>}
      </button>
      {err && <div className="text-xs text-danger">{err}</div>}
      {result && (
        <div className="space-y-2 rounded-lg border border-slate-100 p-3 text-sm dark:border-slate-800">
          <div className="font-medium">{result.name}</div>
          <div className="flex flex-wrap items-center gap-1">
            {result.steps.map((s, i) => (
              <span key={i} className="flex items-center gap-1">
                <span className="rounded-md bg-lightblue px-2 py-0.5 text-xs text-brand">{STEP_LABEL[s.type]}</span>
                {i < result.steps.length - 1 && <span className="text-muted">→</span>}
              </span>
            ))}
          </div>
          {result.suggestedCron && (
            <div className="text-xs">Suggested schedule: <span className="font-mono">{result.suggestedCron}</span></div>
          )}
          <ul className="space-y-1 pl-4 text-xs text-muted">
            {result.notes.map((n, i) => <li key={i} className="list-disc">{n}</li>)}
          </ul>
          <p className="text-xs text-muted">Open <span className="font-medium">Workflows → Build workflow</span> to create it with these steps.</p>
        </div>
      )}
    </div>
  )
}

function AskTab() {
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<AskResponse | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function run() {
    setBusy(true); setErr(null); setResult(null)
    try {
      setResult(await api('/api/copilot/ask', { method: 'POST', body: { question } }))
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted">
        Ask about your transfers. Answers are grounded in the tamper-evident audit log and cite the
        exact records — nothing is invented.
      </p>
      <textarea
        className="input h-20 resize-none"
        placeholder="e.g. did the claims file reach Star Health?"
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
      />
      <button className="btn-primary w-full" disabled={busy || !question.trim()} onClick={run}>
        {busy ? <Spinner className="text-white" /> : <><Send size={15} /> Ask</>}
      </button>
      {err && <div className="text-xs text-danger">{err}</div>}
      {result && (
        <div className="space-y-2">
          <div className="rounded-lg bg-lightblue px-3 py-2 text-sm text-ink dark:bg-slate-800 dark:text-slate-100">
            {result.answer}
          </div>
          {result.evidence.length > 0 && (
            <div>
              <div className="mb-1 text-xs font-medium text-muted">Evidence</div>
              <div className="space-y-1">
                {result.evidence.map((e) => (
                  <div key={e.seq} className="rounded border border-slate-100 px-2 py-1 text-xs dark:border-slate-800">
                    <span className="font-mono text-brand">#{e.seq}</span> {e.action}
                    <span className="ml-1 font-mono text-muted">{e.hash.slice(0, 10)}…</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

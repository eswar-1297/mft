import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { UploadCloud, CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react'
import { api } from '../lib/api'
import type { ImportSummary } from '../lib/types'
import { Card, ErrorBanner, Spinner } from '../components/ui'

export default function LegacyImport() {
  const navigate = useNavigate()
  const [source, setSource] = useState('MOVEIT')
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [summary, setSummary] = useState<ImportSummary | null>(null)

  async function run() {
    if (!file) return
    setBusy(true); setErr(null); setSummary(null)
    try {
      const form = new FormData()
      form.append('file', file)
      form.append('source', source)
      setSummary(await api<ImportSummary>('/api/legacy-import', { method: 'POST', body: form, raw: true }))
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Migrate from MOVEit / GoAnywhere</h1>
        <p className="text-sm text-muted">
          Upload your incumbent's config export. We rebuild each job as a CloudFuze workflow —
          your broken transfer jobs land here automatically as a starting point.
        </p>
      </div>

      <Card>
        <ErrorBanner message={err} />
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="label">Source system</label>
            <select className="input" value={source} onChange={(e) => setSource(e.target.value)}>
              <option value="MOVEIT">MOVEit (Automation tasks)</option>
              <option value="GOANYWHERE">GoAnywhere (projects)</option>
            </select>
          </div>
          <div className="col-span-2">
            <label className="label">Config export (.xml)</label>
            <label className="flex cursor-pointer items-center gap-2 rounded-lg border-2 border-dashed border-slate-200 px-3 py-2 text-sm hover:border-brand dark:border-slate-700">
              <UploadCloud size={18} className="text-brand" />
              {file ? file.name : 'Choose file…'}
              <input type="file" className="hidden" accept=".xml,text/xml" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </label>
          </div>
        </div>
        <button className="btn-primary mt-4" disabled={!file || busy} onClick={run}>
          {busy ? <Spinner className="text-white" /> : 'Convert jobs to workflows'}
        </button>
      </Card>

      {summary && (
        <>
          <div className="grid grid-cols-3 gap-4">
            <div className="card p-5 text-center">
              <div className="text-3xl font-semibold text-brand">{summary.imported}</div>
              <div className="text-xs uppercase tracking-wide text-muted">jobs imported</div>
            </div>
            <div className="card p-5 text-center">
              <div className="text-3xl font-semibold text-warning">{summary.needsReview}</div>
              <div className="text-xs uppercase tracking-wide text-muted">need review</div>
            </div>
            <div className="card p-5 text-center">
              <div className="text-3xl font-semibold">{summary.totalJobs}</div>
              <div className="text-xs uppercase tracking-wide text-muted">total in file</div>
            </div>
          </div>

          <Card>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">Imported workflows</h2>
              <button className="text-sm font-medium text-brand hover:underline" onClick={() => navigate('/workflows')}>
                Open Workflows <ArrowRight size={13} className="inline" />
              </button>
            </div>
            <div className="space-y-3">
              {summary.jobs.map((j) => (
                <div key={j.workflowId} className="rounded-lg border border-slate-100 p-3 dark:border-slate-800">
                  <div className="flex items-center gap-2">
                    {j.status === 'IMPORTED' ? (
                      <CheckCircle2 size={16} className="text-success" />
                    ) : (
                      <AlertTriangle size={16} className="text-warning" />
                    )}
                    <span className="font-medium">{j.name}</span>
                    <span className="text-xs text-muted">· {j.stepCount} steps</span>
                  </div>
                  {j.notes.length > 0 && (
                    <ul className="mt-2 space-y-1 pl-6 text-xs text-muted">
                      {j.notes.map((n, i) => <li key={i} className="list-disc">{n}</li>)}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          </Card>
        </>
      )}
    </div>
  )
}

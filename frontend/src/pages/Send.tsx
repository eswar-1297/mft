import { useState } from 'react'
import { Copy, UploadCloud, CheckCircle2 } from 'lucide-react'
import { api } from '../lib/api'
import type { UploadResult } from '../lib/types'
import { Card, ErrorBanner, Spinner } from '../components/ui'
import { formatBytes } from '../lib/format'

export default function Send() {
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [results, setResults] = useState<UploadResult[]>([])
  const [copied, setCopied] = useState<string | null>(null)

  async function upload() {
    if (!file) return
    setBusy(true)
    setErr(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const result = await api<UploadResult>('/api/files', { method: 'POST', body: form, raw: true })
      setResults((prev) => [result, ...prev])
      setFile(null)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  function copyKey(key: string) {
    navigator.clipboard.writeText(key)
    setCopied(key)
    setTimeout(() => setCopied(null), 1500)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Ad-hoc Send</h1>
        <p className="text-sm text-muted">
          Upload a file into secure storage, then push it to a partner from the Transfers page. Each
          upload is hashed (SHA-256) and audited.
        </p>
      </div>

      <Card>
        <ErrorBanner message={err} />
        <label className="mt-2 flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-200 py-12 text-center hover:border-brand dark:border-slate-700">
          <UploadCloud className="mb-2 text-brand" size={36} />
          <span className="text-sm font-medium">
            {file ? file.name : 'Click to choose a file'}
          </span>
          <span className="mt-1 text-xs text-muted">
            {file ? formatBytes(file.size) : 'Any file type'}
          </span>
          <input
            type="file"
            className="hidden"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <button className="btn-primary mt-4 w-full" disabled={!file || busy} onClick={upload}>
          {busy ? <Spinner className="text-white" /> : 'Upload to secure storage'}
        </button>
      </Card>

      {results.length > 0 && (
        <Card>
          <h2 className="mb-3 font-semibold">Uploaded (this session)</h2>
          <div className="space-y-2">
            {results.map((r) => (
              <div
                key={r.key}
                className="flex items-center justify-between rounded-lg border border-slate-100 px-4 py-3 dark:border-slate-800"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2 font-medium">
                    <CheckCircle2 className="text-success" size={16} /> {r.filename}
                  </div>
                  <div className="mt-0.5 truncate text-xs text-muted">
                    {formatBytes(r.size)} · sha256 {r.sha256.slice(0, 16)}…
                  </div>
                  <div className="mt-0.5 truncate font-mono text-xs text-muted">key: {r.key}</div>
                </div>
                <button className="btn-ghost !py-1 text-xs" onClick={() => copyKey(r.key)}>
                  <Copy size={14} /> {copied === r.key ? 'Copied' : 'Copy key'}
                </button>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}

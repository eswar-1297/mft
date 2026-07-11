// Mirrors the backend API contracts (see the Spring controllers).

export interface User {
  id: string
  email: string
  fullName: string
  role: string
  tenantId: string
  mfaEnabled: boolean
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type TransferStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface Transfer {
  id: string
  direction: 'SFTP_PULL' | 'SFTP_PUSH' | 'FTPS_PULL' | 'FTPS_PUSH' | 'AS2_SEND' | 'AS2_RECEIVE'
  status: TransferStatus
  sourceRef: string
  destRef: string | null
  filename: string
  bytesTransferred: number
  checksumSha256: string | null
  errorMessage: string | null
  attempts: number
  createdBy: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface AuditEvent {
  seq: number
  occurredAt: string
  actorEmail: string | null
  action: string
  resourceType: string | null
  resourceId: string | null
  detailsJson: string | null
  hash: string
  prevHash: string
}

export interface ChainVerification {
  valid: boolean
  recordCount: number
  brokenAtSeq: number | null
  message: string
}

export interface UploadResult {
  key: string
  sha256: string
  size: number
  filename: string
}

export interface SftpDetails {
  host: string
  port: number
  username: string
  password: string
}

export interface Partner {
  id: string
  name: string
  protocol: string
  host: string
  port: number
  username: string
  hasStoredSecret: boolean
  createdAt: string
}

export interface As2Partner {
  id: string
  name: string
  partnerAs2Id: string
  partnerCertificatePem: string
  inboundUrl: string
  createdAt: string
}

export interface As2Identity {
  as2Id: string
  certificatePem: string
}

export interface DraftWorkflowResponse {
  name: string
  steps: WorkflowStep[]
  suggestedCron: string | null
  notes: string[]
  aiAssisted: boolean
}

export interface AuditEvidence {
  seq: number
  action: string
  occurredAt: string
  detail: string
  hash: string
}

export interface AskResponse {
  answer: string
  evidence: AuditEvidence[]
  aiAssisted: boolean
}

export interface ImportedJob {
  name: string
  workflowId: string
  status: 'IMPORTED' | 'NEEDS_REVIEW'
  stepCount: number
  notes: string[]
}

export interface ImportSummary {
  source: string
  totalJobs: number
  imported: number
  needsReview: number
  jobs: ImportedJob[]
}

export interface SiemConfig {
  configured: boolean
  enabled: boolean
  type: string
  target: string
  hasToken: boolean
  lastForwardedSeq: number
  lastStatus: string | null
  lastForwardedAt: string | null
}

export interface Connector {
  id: string
  name: string
  type: string
  endpoint: string | null
  region: string | null
  bucket: string | null
  accessKey: string | null
  pathStyle: boolean
  hasSecret: boolean
  createdAt: string
}

export type StepType =
  | 'PICKUP'
  | 'PGP_ENCRYPT'
  | 'PGP_DECRYPT'
  | 'VALIDATE'
  | 'SEND'
  | 'ARCHIVE'
  | 'NOTIFY'

export interface WorkflowStep {
  type: StepType
  config: Record<string, string>
}

export interface Workflow {
  id: string
  name: string
  steps: WorkflowStep[]
  enabled: boolean
  createdAt: string
  lastRunAt: string | null
  lastStatus: string | null
  cronSchedule: string | null
  as2TriggerPartnerId: string | null
}

export interface StepResult {
  index: number
  type: StepType
  status: 'SUCCEEDED' | 'FAILED'
  detail: string
}

export interface WorkflowRun {
  id: string
  workflowId: string
  workflowName: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  stepResultsJson: string | null
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

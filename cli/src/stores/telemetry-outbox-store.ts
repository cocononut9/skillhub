import { readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { lock } from 'proper-lockfile'
import type { SkillUsageEventRequest } from '../clients/skillhub-client'
import { applyCredentialPermissions, ensureDir, joinPath, pathExists, userStateDir } from '../platform/paths'

export interface QueuedSkillUsageEvent extends SkillUsageEventRequest {
  registry: string
  queuedAt: string
}

interface TelemetryOutbox {
  items: QueuedSkillUsageEvent[]
}

const MAX_OUTBOX_ITEMS = 1000
const MAX_OUTBOX_AGE_MS = 35 * 24 * 60 * 60 * 1000

export class TelemetryOutboxStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'telemetry-outbox.json')
  }

  async read(): Promise<TelemetryOutbox> {
    if (!(await pathExists(this.path))) return { items: [] }
    try {
      const value = JSON.parse(await readFile(this.path, 'utf8')) as TelemetryOutbox
      return Array.isArray(value.items) ? value : { items: [] }
    } catch {
      return { items: [] }
    }
  }

  async enqueue(events: QueuedSkillUsageEvent[], now = Date.now()): Promise<void> {
    if (events.length === 0) return
    await this.mutate(outbox => {
      const minimumTime = now - MAX_OUTBOX_AGE_MS
      const byId = new Map(
        outbox.items
          .filter(item => Date.parse(item.queuedAt) >= minimumTime)
          .map(item => [item.eventId, item])
      )
      for (const event of events) {
        const existing = byId.get(event.eventId)
        if (!existing || event.evidenceType === 'SCRIPT_EXECUTED') {
          byId.set(event.eventId, event)
        }
      }
      outbox.items = [...byId.values()]
        .sort((left, right) => left.queuedAt.localeCompare(right.queuedAt))
        .slice(-MAX_OUTBOX_ITEMS)
    })
  }

  async remove(eventIds: Set<string>): Promise<void> {
    if (eventIds.size === 0) return
    await this.mutate(outbox => {
      outbox.items = outbox.items.filter(item => !eventIds.has(item.eventId))
    })
  }

  private async mutate(change: (outbox: TelemetryOutbox) => void): Promise<void> {
    await ensureDir(dirname(this.path))
    let release: (() => Promise<void>) | null = null
    try {
      release = await lock(this.path, {
        lockfilePath: `${this.path}.lock`,
        realpath: false,
        stale: 30_000,
        retries: { retries: 5, minTimeout: 50, maxTimeout: 300 }
      })
      const outbox = await this.read()
      change(outbox)
      const temporaryPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
      try {
        await writeFile(temporaryPath, JSON.stringify(outbox, null, 2))
        await applyCredentialPermissions(temporaryPath)
        await rename(temporaryPath, this.path)
        await applyCredentialPermissions(this.path)
      } finally {
        await rm(temporaryPath, { force: true }).catch(() => {})
      }
    } finally {
      if (release) await release().catch(() => {})
    }
  }
}

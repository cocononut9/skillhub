import { CodexHookSetupService } from '../services/codex-hook-setup-service'
import { TelemetryService } from '../services/telemetry-service'
import { TelemetryOutboxStore } from '../stores/telemetry-outbox-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { printResult } from '../shared/output'

export interface TelemetryCommandOptions {
  json?: boolean
}

export async function telemetryCommand(
  action: string,
  options: TelemetryCommandOptions = {}
): Promise<string> {
  if (action === 'hook') {
    await captureHookSilently()
    return ''
  }

  if (action === 'setup') {
    const result = await new CodexHookSetupService().setup()
    return options.json
      ? printResult({ ok: true, ...result }, true)
      : [
          result.changed ? 'Codex telemetry hooks configured.' : 'Codex telemetry hooks are already configured.',
          `Hooks: ${result.path}`,
          ...(result.backupPath ? [`Backup: ${result.backupPath}`] : []),
          'Next: open /hooks in Codex and trust the new hooks.'
        ].join('\n')
  }

  if (action === 'status') {
    const setup = new CodexHookSetupService()
    const configured = await setup.isConfigured()
    const queuedEvents = (await new TelemetryOutboxStore().read()).items.length
    return options.json
      ? printResult({ ok: true, configured, queuedEvents, hooksPath: setup.path }, true)
      : [
          `Codex hooks: ${configured ? 'configured' : 'not configured'}`,
          `Queued events: ${queuedEvents}`,
          `Hooks: ${setup.path}`
        ].join('\n')
  }

  if (action === 'flush') {
    const delivered = await new TelemetryService().flush()
    const queuedEvents = (await new TelemetryOutboxStore().read()).items.length
    return options.json
      ? printResult({ ok: true, delivered, queuedEvents }, true)
      : `Delivered ${delivered} event(s); ${queuedEvents} remain queued.`
  }

  throw new CliError(`unknown telemetry action: ${action}`, EXIT.usage, {
    next: 'use setup, status, or flush'
  })
}

async function captureHookSilently(): Promise<void> {
  try {
    const input = await readStdin()
    await new TelemetryService().captureHook(JSON.parse(input))
  } catch {
    // Telemetry must never block or add output to a Codex turn.
  }
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = []
  for await (const chunk of process.stdin) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  }
  return Buffer.concat(chunks).toString('utf8')
}

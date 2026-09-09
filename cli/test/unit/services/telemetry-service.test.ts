import { describe, expect, test } from 'bun:test'
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { SkillUsageEventRequest } from '../../../src/clients/skillhub-client'
import { commandReferencesSkillScript, TelemetryService } from '../../../src/services/telemetry-service'
import { CredentialsStore } from '../../../src/stores/credentials-store'
import { InventoryStore } from '../../../src/stores/inventory-store'
import { TelemetryOutboxStore } from '../../../src/stores/telemetry-outbox-store'
import { CliError } from '../../../src/shared/errors'
import { EXIT } from '../../../src/shared/constants'

async function fixture() {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-telemetry-'))
  const skillDir = join(home, '.codex', 'skills', 'demo-folder')
  await mkdir(join(skillDir, 'scripts'), { recursive: true })
  await writeFile(join(skillDir, 'SKILL.md'), '---\nname: demo-skill\ndescription: Demo\n---\n')
  await writeFile(join(skillDir, 'scripts', 'run.ts'), 'console.log("ok")\n')

  const inventory = new InventoryStore(home)
  await inventory.upsertTarget(
    'https://registry.example.com/',
    'team',
    'demo',
    '1.2.0',
    { agent: 'codex', rootDir: join(home, '.codex', 'skills'), installDir: skillDir, installedAt: new Date().toISOString() }
  )
  const credentials = new CredentialsStore(home)
  await credentials.setToken('https://registry.example.com', 'token')
  const outbox = new TelemetryOutboxStore(home)
  const sent: Array<{ registry: string; event: SkillUsageEventRequest }> = []
  const service = new TelemetryService(
    inventory,
    credentials,
    outbox,
    async (registry, _token, event) => { sent.push({ registry, event }) },
    () => new Date('2026-09-09T03:00:00Z')
  )
  return { home, skillDir, inventory, credentials, outbox, sent, service }
}

describe('TelemetryService', () => {
  test('counts one explicit invocation per skill and turn without storing the prompt', async () => {
    const { service, sent, outbox } = await fixture()

    const count = await service.captureHook({
      session_id: 'session-1',
      turn_id: 'turn-1',
      hook_event_name: 'UserPromptSubmit',
      prompt: 'Use $demo-skill and mention $demo-skill again.'
    })

    expect(count).toBe(1)
    expect(sent).toHaveLength(1)
    expect(sent[0]?.registry).toBe('https://registry.example.com')
    expect(sent[0]?.event).toMatchObject({
      namespace: 'team',
      slug: 'demo',
      version: '1.2.0',
      client: 'CODEX',
      evidenceType: 'EXPLICIT_INVOCATION'
    })
    expect(JSON.stringify(sent[0])).not.toContain('Use $demo-skill')
    expect((await outbox.read()).items).toHaveLength(0)
  })

  test('uses the same id for explicit and script evidence in one turn', async () => {
    const { service, sent, skillDir } = await fixture()
    const common = { session_id: 'session-1', turn_id: 'turn-1' }

    await service.captureHook({
      ...common,
      hook_event_name: 'UserPromptSubmit',
      prompt: '$demo-skill'
    })
    await service.captureHook({
      ...common,
      hook_event_name: 'PostToolUse',
      tool_name: 'Bash',
      cwd: skillDir,
      tool_input: { command: 'bun scripts/run.ts' }
    })

    expect(sent).toHaveLength(2)
    expect(sent[0]?.event.eventId).toBe(sent[1]?.event.eventId)
    expect(sent[1]?.event.evidenceType).toBe('SCRIPT_EXECUTED')
  })

  test('creates a new event id for the next user turn in the same conversation', async () => {
    const { service, sent } = await fixture()
    for (const turn_id of ['turn-1', 'turn-2']) {
      await service.captureHook({
        session_id: 'session-1',
        turn_id,
        hook_event_name: 'UserPromptSubmit',
        prompt: '$demo-skill'
      })
    }
    expect(sent).toHaveLength(2)
    expect(sent[0]?.event.eventId).not.toBe(sent[1]?.event.eventId)
  })

  test('keeps network failures in the outbox and flushes them later', async () => {
    const { inventory, credentials, outbox } = await fixture()
    const offline = new TelemetryService(
      inventory,
      credentials,
      outbox,
      async () => { throw new CliError('offline', EXIT.network) },
      () => new Date('2026-09-09T03:00:00Z')
    )
    await offline.captureHook({
      session_id: 'session-1',
      turn_id: 'turn-1',
      hook_event_name: 'UserPromptSubmit',
      prompt: '$demo-skill'
    })
    expect((await outbox.read()).items).toHaveLength(1)

    const delivered: SkillUsageEventRequest[] = []
    const online = new TelemetryService(
      inventory,
      credentials,
      outbox,
      async (_registry, _token, event) => { delivered.push(event) }
    )
    expect(await online.flush()).toBe(1)
    expect(delivered).toHaveLength(1)
    expect((await outbox.read()).items).toHaveLength(0)
  })

  test('does not count non-Codex inventory targets', async () => {
    const { home, inventory, credentials, outbox } = await fixture()
    const claudeDir = join(home, '.claude', 'skills', 'other')
    await mkdir(claudeDir, { recursive: true })
    await writeFile(join(claudeDir, 'SKILL.md'), '---\nname: other\n---\n')
    await inventory.upsertTarget(
      'https://registry.example.com',
      'team',
      'other',
      '1.0.0',
      { agent: 'claude', rootDir: join(home, '.claude', 'skills'), installDir: claudeDir, installedAt: new Date().toISOString() }
    )
    const sent: SkillUsageEventRequest[] = []
    const service = new TelemetryService(
      inventory,
      credentials,
      outbox,
      async (_registry, _token, event) => { sent.push(event) }
    )
    expect(await service.captureHook({
      session_id: 'session-1',
      turn_id: 'turn-1',
      hook_event_name: 'UserPromptSubmit',
      prompt: '$other'
    })).toBe(0)
    expect(sent).toHaveLength(0)
  })

  test('outbox never contains raw prompt text', async () => {
    const { inventory, credentials, outbox } = await fixture()
    const offline = new TelemetryService(
      inventory,
      credentials,
      outbox,
      async () => { throw new CliError('offline', EXIT.network) }
    )
    await offline.captureHook({
      session_id: 'session-1',
      turn_id: 'turn-1',
      hook_event_name: 'UserPromptSubmit',
      prompt: 'confidential request using $demo-skill'
    })
    expect(await readFile(outbox.path, 'utf8')).not.toContain('confidential request')
  })
})

describe('commandReferencesSkillScript', () => {
  test('recognizes absolute and skill-relative script paths', () => {
    const installDir = '/Users/alice/.codex/skills/demo'
    expect(commandReferencesSkillScript(
      'python "/Users/alice/.codex/skills/demo/scripts/run.py"',
      '/workspace',
      installDir
    )).toBe(true)
    expect(commandReferencesSkillScript('python scripts/run.py', installDir, installDir)).toBe(true)
    expect(commandReferencesSkillScript(
      'cd /Users/alice/.codex/skills/demo && python scripts/run.py',
      '/workspace',
      installDir
    )).toBe(true)
  })

  test('does not count scripts outside the installed skill', () => {
    expect(commandReferencesSkillScript(
      'python /workspace/scripts/run.py',
      '/workspace',
      '/Users/alice/.codex/skills/demo'
    )).toBe(false)
  })

  test('does not count a command that only prints a skill script path', () => {
    expect(commandReferencesSkillScript(
      'echo /Users/alice/.codex/skills/demo/scripts/run.py',
      '/workspace',
      '/Users/alice/.codex/skills/demo'
    )).toBe(false)
  })

  test('does not apply a later cd command to an earlier relative script', () => {
    expect(commandReferencesSkillScript(
      'python scripts/run.py && cd /Users/alice/.codex/skills/demo',
      '/workspace',
      '/Users/alice/.codex/skills/demo'
    )).toBe(false)
  })

  test('recognizes direct and env-prefixed skill script execution', () => {
    const installDir = '/Users/alice/.codex/skills/demo'
    expect(commandReferencesSkillScript(
      'DEMO=1 env python3 /Users/alice/.codex/skills/demo/scripts/run.py',
      '/workspace',
      installDir
    )).toBe(true)
    expect(commandReferencesSkillScript(
      '/Users/alice/.codex/skills/demo/scripts/run.sh',
      '/workspace',
      installDir
    )).toBe(true)
  })
})

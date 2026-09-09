import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { basename, isAbsolute, relative, resolve } from 'node:path'
import {
  SkillHubClient,
  type SkillUsageEventRequest,
  type SkillUsageEvidenceType
} from '../clients/skillhub-client'
import { CredentialsStore } from '../stores/credentials-store'
import { InventoryStore, type InventoryItem, type InventoryTarget } from '../stores/inventory-store'
import { TelemetryOutboxStore, type QueuedSkillUsageEvent } from '../stores/telemetry-outbox-store'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

interface CodexHookInput {
  session_id: string
  turn_id: string
  cwd?: string
  hook_event_name: 'UserPromptSubmit' | 'PostToolUse'
  prompt?: string
  tool_name?: string
  tool_input?: { command?: string }
}

interface InstalledCodexSkill {
  item: InventoryItem
  target: InventoryTarget
  invocationName: string
}

type SendUsageEvent = (
  registry: string,
  token: string,
  event: SkillUsageEventRequest
) => Promise<void>

const MAX_FLUSH_PER_HOOK = 25
const SCRIPT_RUNNERS = new Set([
  'bash', 'bun', 'deno', 'node', 'perl', 'php', 'powershell', 'pwsh',
  'python', 'python2', 'python3', 'ruby', 'sh', 'tsx', 'zsh'
])

export class TelemetryService {
  constructor(
    private readonly inventoryStore = new InventoryStore(),
    private readonly credentialsStore = new CredentialsStore(),
    private readonly outboxStore = new TelemetryOutboxStore(),
    private readonly sendUsageEvent: SendUsageEvent = async (registry, token, event) => {
      await new SkillHubClient(registry, token).reportSkillUsage(event)
    },
    private readonly now: () => Date = () => new Date()
  ) {}

  async captureHook(rawInput: unknown): Promise<number> {
    const input = parseHookInput(rawInput)
    if (!input) return 0

    const installedSkills = await this.readInstalledCodexSkills()
    const detected = input.hook_event_name === 'UserPromptSubmit'
      ? detectExplicitInvocations(input.prompt ?? '', installedSkills)
      : detectScriptExecutions(input.tool_name, input.tool_input?.command, input.cwd, installedSkills)

    const capturedAt = this.now().toISOString()
    const events = detected.map(({ skill, evidenceType }): QueuedSkillUsageEvent => ({
      registry: normalizeRegistry(skill.item.registry),
      eventId: createEventId(skill.item, input.session_id, input.turn_id),
      namespace: skill.item.namespace,
      slug: skill.item.slug,
      version: skill.item.version,
      client: 'CODEX',
      evidenceType,
      occurredAt: capturedAt,
      queuedAt: capturedAt
    }))

    await this.outboxStore.enqueue(events)
    await this.flush()
    return events.length
  }

  async flush(): Promise<number> {
    const outbox = await this.outboxStore.read()
    const delivered = new Set<string>()

    for (const queued of outbox.items.slice(0, MAX_FLUSH_PER_HOOK)) {
      const token = await this.credentialsStore.getToken(queued.registry)
      if (!token) continue

      const event: SkillUsageEventRequest = {
        eventId: queued.eventId,
        namespace: queued.namespace,
        slug: queued.slug,
        version: queued.version,
        client: queued.client,
        evidenceType: queued.evidenceType,
        occurredAt: queued.occurredAt
      }
      try {
        await this.sendUsageEvent(queued.registry, token, event)
        delivered.add(queued.eventId)
      } catch (error) {
        if (error instanceof CliError && error.exitCode !== EXIT.auth && error.exitCode !== EXIT.network) {
          delivered.add(queued.eventId)
        }
      }
    }

    await this.outboxStore.remove(delivered)
    return delivered.size
  }

  private async readInstalledCodexSkills(): Promise<InstalledCodexSkill[]> {
    let inventory
    try {
      inventory = await this.inventoryStore.read()
    } catch {
      return []
    }

    const installed: InstalledCodexSkill[] = []
    for (const item of inventory.items) {
      for (const target of item.targets.filter(candidate => candidate.agent.toLowerCase() === 'codex')) {
        installed.push({
          item,
          target,
          invocationName: await readSkillName(target.installDir) ?? item.slug
        })
      }
    }
    return installed
  }
}

function parseHookInput(value: unknown): CodexHookInput | null {
  if (!isRecord(value)) return null
  if (typeof value.session_id !== 'string' || value.session_id.length === 0) return null
  if (typeof value.turn_id !== 'string' || value.turn_id.length === 0) return null
  if (value.hook_event_name !== 'UserPromptSubmit' && value.hook_event_name !== 'PostToolUse') return null

  return value as unknown as CodexHookInput
}

async function readSkillName(installDir: string): Promise<string | null> {
  try {
    const content = await readFile(resolve(installDir, 'SKILL.md'), 'utf8')
    const frontmatter = content.match(/^---\s*\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/)
    if (!frontmatter?.[1]) return null
    const nameLine = frontmatter[1].match(/^name\s*:\s*(.+?)\s*$/m)
    if (!nameLine?.[1]) return null
    const name = nameLine[1].replace(/^(?:"([\s\S]*)"|'([\s\S]*)')$/, '$1$2').trim()
    return name.length > 0 ? name : null
  } catch {
    return null
  }
}

function detectExplicitInvocations(
  prompt: string,
  installedSkills: InstalledCodexSkill[]
): Array<{ skill: InstalledCodexSkill; evidenceType: SkillUsageEvidenceType }> {
  const mentionedNames = new Set<string>()
  for (const match of prompt.matchAll(/(?:^|\s)\$([a-z0-9][a-z0-9._:-]*)/gi)) {
    if (match[1]) mentionedNames.add(match[1].toLowerCase())
  }

  const byInvocationName = new Map<string, InstalledCodexSkill[]>()
  for (const skill of installedSkills) {
    const name = skill.invocationName.toLowerCase()
    const existing = byInvocationName.get(name) ?? []
    if (!existing.some(candidate => skillIdentity(candidate) === skillIdentity(skill))) {
      existing.push(skill)
    }
    byInvocationName.set(name, existing)
  }

  return [...mentionedNames].flatMap(name => {
    const matches = byInvocationName.get(name) ?? []
    return matches.length === 1
      ? [{ skill: matches[0]!, evidenceType: 'EXPLICIT_INVOCATION' as const }]
      : []
  })
}

function detectScriptExecutions(
  toolName: string | undefined,
  command: string | undefined,
  cwd: string | undefined,
  installedSkills: InstalledCodexSkill[]
): Array<{ skill: InstalledCodexSkill; evidenceType: SkillUsageEvidenceType }> {
  if (toolName !== 'Bash' || !command) return []

  const unique = new Map<string, InstalledCodexSkill>()
  for (const skill of installedSkills) {
    if (commandReferencesSkillScript(command, cwd ?? process.cwd(), skill.target.installDir)) {
      unique.set(skillIdentity(skill), skill)
    }
  }
  return [...unique.values()].map(skill => ({ skill, evidenceType: 'SCRIPT_EXECUTED' }))
}

export function commandReferencesSkillScript(command: string, cwd: string, installDir: string): boolean {
  const home = process.env.HOME ?? process.env.USERPROFILE ?? ''
  const expandedCommand = command
    .replace(/\$\{HOME\}|\$HOME/g, home)
    .replace(/(^|[\s"'])~(?=\/)/g, `$1${home}`)
    .replace(/\\/g, '/')
  const scriptsDir = resolve(installDir, 'scripts')

  let commandCwd = resolve(cwd)
  for (const segment of expandedCommand.split(/&&|\|\||[;|\n]/)) {
    const tokens = shellLikeTokens(segment)
    const executableIndex = findExecutableIndex(tokens)
    if (executableIndex < 0) continue

    const executable = tokens[executableIndex]!
    if (basename(executable).toLowerCase() === 'cd') {
      const directory = tokens[executableIndex + 1]
      if (directory) commandCwd = isAbsolute(directory) ? resolve(directory) : resolve(commandCwd, directory)
      continue
    }

    if (isSkillScriptPath(executable, commandCwd, scriptsDir)) return true

    const runner = basename(executable).toLowerCase()
    if (!SCRIPT_RUNNERS.has(runner)) continue
    if (tokens.slice(executableIndex + 1).some(token => isSkillScriptPath(token, commandCwd, scriptsDir))) {
      return true
    }
  }
  return false
}

function findExecutableIndex(tokens: string[]): number {
  let index = 0
  while (index < tokens.length && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[index]!)) index++

  while (index < tokens.length) {
    const command = basename(tokens[index]!).toLowerCase()
    if (command !== 'env' && command !== 'sudo' && command !== 'command') break
    index++
    while (index < tokens.length && (tokens[index]!.startsWith('-') || /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[index]!))) {
      index++
    }
  }
  return index < tokens.length ? index : -1
}

function isSkillScriptPath(token: string, base: string, scriptsDir: string): boolean {
  const pathToken = stripShellToken(token)
  if (!pathToken || pathToken.startsWith('-')) return false

  const candidate = isAbsolute(pathToken) ? resolve(pathToken) : resolve(base, pathToken)
  return isInside(candidate, scriptsDir)
}

function shellLikeTokens(command: string): string[] {
  return (command.match(/"[^"]*"|'[^']*'|[^\s;&|()<>]+/g) ?? [])
    .map(stripShellToken)
    .filter(Boolean)
}

function stripShellToken(token: string): string {
  return token
    .replace(/^["']|["']$/g, '')
    .replace(/[,:]+$/, '')
}

function isInside(candidate: string, directory: string): boolean {
  const path = relative(resolve(directory), resolve(candidate))
  return path !== '' && path !== '..' && !path.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)
}

function createEventId(item: InventoryItem, sessionId: string, turnId: string): string {
  return createHash('sha256')
    .update([
      normalizeRegistry(item.registry),
      item.namespace,
      item.slug,
      sessionId,
      turnId
    ].join('\u0000'))
    .digest('hex')
}

function skillIdentity(skill: InstalledCodexSkill): string {
  return [normalizeRegistry(skill.item.registry), skill.item.namespace, skill.item.slug].join('\u0000')
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

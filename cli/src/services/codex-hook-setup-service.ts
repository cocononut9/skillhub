import { copyFile, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { ensureDir, joinPath, pathExists } from '../platform/paths'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'

interface HookHandler {
  type: 'command'
  command: string
  commandWindows?: string
  async: boolean
  timeout: number
}

interface HookGroup {
  matcher?: string
  hooks: HookHandler[]
}

interface CodexHooksConfig {
  [key: string]: unknown
  description?: string
  hooks: Record<string, HookGroup[]>
}

export interface CodexHookSetupResult {
  path: string
  backupPath?: string
  changed: boolean
}

export class CodexHookSetupService {
  readonly path: string

  constructor(
    home = process.env.HOME || process.env.USERPROFILE || '',
    private readonly executablePath = process.execPath,
    private readonly entryPath = process.argv[1] ? resolve(process.argv[1]) : ''
  ) {
    if (!home) throw new CliError('cannot resolve user home directory', EXIT.filesystem)
    this.path = joinPath(home, '.codex', 'hooks.json')
  }

  async setup(): Promise<CodexHookSetupResult> {
    if (!this.entryPath) throw new CliError('cannot resolve SkillHub CLI entry point', EXIT.filesystem)
    const existed = await pathExists(this.path)
    const config = existed ? await this.readConfig() : { hooks: {} }
    const handler = this.createHandler()
    let changed = false

    changed = addOrUpdateHandler(config.hooks, 'UserPromptSubmit', undefined, handler) || changed
    changed = addOrUpdateHandler(config.hooks, 'PostToolUse', '^Bash$', handler) || changed
    if (!changed) return { path: this.path, changed: false }

    await ensureDir(dirname(this.path))
    let backupPath: string | undefined
    if (existed) {
      backupPath = `${this.path}.skillhub.bak`
      await copyFile(this.path, backupPath)
    }

    const temporaryPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
    try {
      await writeFile(temporaryPath, JSON.stringify(config, null, 2))
      JSON.parse(await readFile(temporaryPath, 'utf8'))
      await rename(temporaryPath, this.path)
    } finally {
      await rm(temporaryPath, { force: true }).catch(() => {})
    }
    return { path: this.path, ...(backupPath ? { backupPath } : {}), changed: true }
  }

  async isConfigured(): Promise<boolean> {
    if (!(await pathExists(this.path))) return false
    const config = await this.readConfig()
    return hasSkillHubHandler(config.hooks.UserPromptSubmit)
      && hasSkillHubHandler(config.hooks.PostToolUse)
  }

  private async readConfig(): Promise<CodexHooksConfig> {
    let value: unknown
    try {
      value = JSON.parse(await readFile(this.path, 'utf8'))
    } catch {
      throw new CliError(`Codex hooks file is not valid JSON: ${this.path}`, EXIT.validation, {
        next: 'repair the file before running `skillhub telemetry setup`'
      })
    }
    if (!isRecord(value) || (value.hooks !== undefined && !isRecord(value.hooks))) {
      throw new CliError(`Codex hooks file has an unsupported shape: ${this.path}`, EXIT.validation)
    }
    const root = value as Record<string, unknown>
    return { ...root, hooks: (root.hooks ?? {}) as Record<string, HookGroup[]> }
  }

  private createHandler(): HookHandler {
    return {
      type: 'command',
      command: `${quotePosix(this.executablePath)} ${quotePosix(this.entryPath)} telemetry hook`,
      commandWindows: `${quoteWindows(this.executablePath)} ${quoteWindows(this.entryPath)} telemetry hook`,
      async: true,
      timeout: 10
    }
  }
}

function addOrUpdateHandler(
  hooks: Record<string, HookGroup[]>,
  eventName: string,
  matcher: string | undefined,
  handler: HookHandler
): boolean {
  const groups = Array.isArray(hooks[eventName]) ? hooks[eventName]! : []
  for (const group of groups) {
    if (!isRecord(group) || !Array.isArray(group.hooks)) continue
    const existing = group.hooks.find(candidate => isSkillHubHandler(candidate))
    if (existing) {
      const matcherChanged = matcher !== undefined && group.matcher !== matcher
      const handlerChanged = JSON.stringify(existing) !== JSON.stringify(handler)
      if (matcherChanged) group.matcher = matcher
      if (handlerChanged) Object.assign(existing, handler)
      return matcherChanged || handlerChanged
    }
  }

  groups.push({ ...(matcher ? { matcher } : {}), hooks: [handler] })
  hooks[eventName] = groups
  return true
}

function hasSkillHubHandler(groups: HookGroup[] | undefined): boolean {
  return Array.isArray(groups) && groups.some(group =>
    isRecord(group) && Array.isArray(group.hooks) && group.hooks.some(isSkillHubHandler))
}

function isSkillHubHandler(value: unknown): value is HookHandler {
  return isRecord(value)
    && value.type === 'command'
    && typeof value.command === 'string'
    && /\btelemetry\s+hook\s*$/.test(value.command)
}

function quotePosix(value: string): string {
  return `'${value.replace(/'/g, `'"'"'`)}'`
}

function quoteWindows(value: string): string {
  return `"${value.replace(/"/g, '\\"')}"`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

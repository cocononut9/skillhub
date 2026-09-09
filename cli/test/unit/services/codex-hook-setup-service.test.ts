import { describe, expect, test } from 'bun:test'
import { mkdtemp, readFile, writeFile, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { CodexHookSetupService } from '../../../src/services/codex-hook-setup-service'

describe('CodexHookSetupService', () => {
  test('preserves existing hooks and adds the two telemetry hooks once', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-hook-setup-'))
    const path = join(home, '.codex', 'hooks.json')
    await mkdir(dirname(path), { recursive: true })
    await writeFile(path, JSON.stringify({
      custom: { keep: true },
      hooks: {
        Stop: [{ hooks: [{ type: 'command', command: 'echo existing' }] }]
      }
    }))

    const service = new CodexHookSetupService(home, '/usr/bin/node', '/opt/skillhub/dist/index.js')
    const first = await service.setup()
    const second = await service.setup()
    const config = JSON.parse(await readFile(path, 'utf8'))

    expect(first.changed).toBe(true)
    expect(first.backupPath).toBe(`${path}.skillhub.bak`)
    expect(second.changed).toBe(false)
    expect(config.custom).toEqual({ keep: true })
    expect(config.hooks.Stop[0].hooks[0].command).toBe('echo existing')
    expect(config.hooks.UserPromptSubmit).toHaveLength(1)
    expect(config.hooks.PostToolUse).toHaveLength(1)
    expect(config.hooks.PostToolUse[0].matcher).toBe('^Bash$')
    expect(config.hooks.PostToolUse[0].hooks[0]).toMatchObject({ async: true, timeout: 10 })
    expect(await service.isConfigured()).toBe(true)
  })

  test('repairs the matcher of an existing SkillHub PostToolUse hook', async () => {
    const home = await mkdtemp(join(tmpdir(), 'skillhub-hook-setup-'))
    const path = join(home, '.codex', 'hooks.json')
    await mkdir(dirname(path), { recursive: true })
    await writeFile(path, JSON.stringify({
      hooks: {
        PostToolUse: [{
          matcher: '^Read$',
          hooks: [{
            type: 'command',
            command: "'/usr/bin/node' '/opt/skillhub/dist/index.js' telemetry hook",
            async: true,
            timeout: 10
          }]
        }]
      }
    }))

    await new CodexHookSetupService(home, '/usr/bin/node', '/opt/skillhub/dist/index.js').setup()
    const config = JSON.parse(await readFile(path, 'utf8'))
    expect(config.hooks.PostToolUse[0].matcher).toBe('^Bash$')
  })
})

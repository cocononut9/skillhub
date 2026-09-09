import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSkillFile } from '@/shared/hooks/use-skill-queries'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'

interface PromptResourceContentProps {
  namespace: string
  slug: string
  version?: string
  enabled: boolean
}

export function PromptResourceContent({ namespace, slug, version, enabled }: PromptResourceContentProps) {
  const { t } = useTranslation()
  const { data: content, isLoading, error } = useSkillFile(namespace, slug, version, 'PROMPT.md', enabled)
  const [copied, setCopied] = useState(false)
  const [copyFailed, setCopyFailed] = useState(false)

  async function copyPrompt() {
    if (!enabled || !content || error) return
    try {
      await copyToClipboard(content)
      setCopied(true)
      setCopyFailed(false)
    } catch {
      setCopied(false)
      setCopyFailed(true)
    }
  }

  return (
    <Card className="mb-6 p-6 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">{t('promptResource.body')}</h2>
        <Button onClick={copyPrompt} disabled={!enabled || isLoading || !!error || !content}>
          {t(copied ? 'promptResource.copied' : 'promptResource.copy')}
        </Button>
      </div>
      {!enabled ? <p className="text-sm text-muted-foreground">{t('promptResource.unavailable')}</p>
        : error ? <p role="alert">{t('promptResource.loadError')}</p>
        : isLoading ? <p>{t('promptResource.loading')}</p>
        : <pre className="max-h-[36rem] overflow-auto whitespace-pre-wrap break-words text-sm leading-relaxed">{content}</pre>}
      {copyFailed && <p role="alert">{t('promptResource.copyError')}</p>}
    </Card>
  )
}

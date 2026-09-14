import { withBasePath } from '@/shared/lib/base-path'
import { cn } from '@/shared/lib/utils'

interface BrandMarkProps {
  className?: string
  imageClassName?: string
  alt?: string
}

/**
 * 使用千岸官网原始 SVG，保持图形和宽高比；页面按主题单色显示，避免原图白字融入浅底。
 */
export function BrandMark({ className, imageClassName, alt = '千岸科技 · 1000shores' }: BrandMarkProps) {
  return (
    <span className={cn('inline-flex shrink-0 items-center justify-center', className)}>
      <img
        src={withBasePath('/brand/1000shores-logo.svg')}
        alt={alt}
        className={cn('company-logo h-full w-full object-contain', imageClassName)}
      />
    </span>
  )
}

// 平台徽标：一个字的小方块，品牌色只用在这里（规范 §3.4 例外）
import { platDef } from '@/data/platforms';
import { cn } from '@/lib/cn';
export function PlatLogo({ k, size = 28, className }: { k: string; size?: number; className?: string }) {
  const p = platDef(k); if (!p) return null;
  return <i aria-hidden className={cn('inline-grid place-items-center flex-none not-italic font-800 leading-none rounded-[min(var(--rk),8px)]', className)} style={{ width: size, height: size, fontSize: Math.round(size * 0.55), background: p.bg, color: p.fg }}>{p.ch}</i>;
}

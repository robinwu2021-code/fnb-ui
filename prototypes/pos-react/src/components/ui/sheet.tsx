// Sheet ← 原型 sh-sheet：右侧 480 侧滑，左侧两角 rc；头（标题 + 关闭）· 页签 · 内容滚动
import * as D from '@radix-ui/react-dialog';
import { cn } from '@/lib/cn';
import { useDeviceEl } from '@/layout/device';
import { Button } from './button';
import { Ico } from '@/lib/icon';
import type { ReactNode } from 'react';

export function Sheet({ open, onClose, title, closeLabel, children, className }: { open: boolean; onClose: () => void; title: ReactNode; closeLabel: string; children: ReactNode; className?: string }) {
  const el = useDeviceEl();
  return (
    <D.Root open={open} onOpenChange={(o) => !o && onClose()}>
      <D.Portal container={el ?? undefined}>
        <D.Overlay className="anim-fade absolute inset-0 z-20 bg-scrim flex justify-end items-stretch">
          <D.Content aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()} className={cn('anim-sheet bg-surface rounded-l-rc w-480px py-pad px-[calc(var(--pad)+4px)] flex flex-col gap-gap overflow-y-auto outline-none [&>*]:flex-shrink-0', className)}>
            <div className="flex justify-between items-center">
              <D.Title className="text-lg font-600 m-0">{title}</D.Title>
              <D.Close asChild><Button variant="muted" size="icon-sm" aria-label={closeLabel}><Ico name="x" size={16} /></Button></D.Close>
            </div>
            {children}
          </D.Content>
        </D.Overlay>
      </D.Portal>
    </D.Root>
  );
}

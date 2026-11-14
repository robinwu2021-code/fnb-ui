// Dialog ← 原型 sh-dialog：遮罩 45% 墨；弹窗白、圆角 rc+4、≤560（wide 760）；标题 18/600，说明次要色，底部操作等分
import * as D from '@radix-ui/react-dialog';
import { cn } from '@/lib/cn';
import { useDeviceEl } from '@/layout/device';
import type { ReactNode } from 'react';

export function Dialog({ open, onClose, wide, children, className, ariaLabel }: { open: boolean; onClose: () => void; wide?: boolean; children: ReactNode; className?: string; ariaLabel?: string }) {
  const el = useDeviceEl();
  return (
    <D.Root open={open} onOpenChange={(o) => !o && onClose()}>
      <D.Portal container={el ?? undefined}>
        <D.Overlay className="anim-fade absolute inset-0 z-20 bg-scrim flex items-center justify-center p-6">
          <D.Content aria-label={ariaLabel} aria-describedby={undefined} onOpenAutoFocus={(e) => e.preventDefault()} className={cn('bg-surface rounded-[calc(var(--rc)+4px)] p-[calc(var(--pad)+8px)] w-full flex flex-col gap-gap outline-none max-h-full overflow-y-auto', wide ? 'max-w-760px' : 'max-w-560px', className)}>
            {children}
          </D.Content>
        </D.Overlay>
      </D.Portal>
    </D.Root>
  );
}
export const DialogTitle = ({ children, className }: { children: ReactNode; className?: string }) => <D.Title className={cn('text-lg font-600 tracking-[-.01em] m-0', className)}>{children}</D.Title>;
export const DialogDesc = ({ children }: { children: ReactNode }) => <D.Description className="text-sub m-0">{children}</D.Description>;
export const DialogActs = ({ children, className }: { children: ReactNode; className?: string }) => <div className={cn('flex gap-gap mt-1 [&>button]:flex-1', className)}>{children}</div>;

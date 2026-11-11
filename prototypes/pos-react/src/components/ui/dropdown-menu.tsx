// Menu ← 原型 sh-menu：反色面（墨底白字），行高 44，危险项红字，禁用项 45% 并写原因
import * as M from '@radix-ui/react-dropdown-menu';
import { cn } from '@/lib/cn';
import { useDeviceEl } from '@/layout/device';
import type { ReactNode } from 'react';

export const Menu = M.Root;
export const MenuTrigger = M.Trigger;
export function MenuContent({ children, align = 'end' }: { children: ReactNode; align?: 'start' | 'end' }) {
  const el = useDeviceEl();
  return (
    <M.Portal container={el ?? undefined}>
      <M.Content align={align} sideOffset={6} className="z-25 bg-inverse text-on-inverse rounded-rk p-1 min-w-220px flex flex-col outline-none">{children}</M.Content>
    </M.Portal>
  );
}
export function MenuItem({ children, hint, danger, disabled, onSelect }: { children: ReactNode; hint?: ReactNode; danger?: boolean; disabled?: boolean; onSelect?: () => void }) {
  return (
    <M.Item disabled={disabled} onSelect={onSelect} className={cn('flex items-center justify-between gap-4 h-ctl px-3 rounded-rk-in text-base cursor-pointer outline-none data-[highlighted]:bg-[rgba(255,255,255,.12)]', danger && 'text-danger-strong', disabled && 'opacity-45 cursor-default')}>
      <span>{children}</span>{hint && <small className="text-xs opacity-60">{hint}</small>}
    </M.Item>
  );
}
export const MenuSep = () => <M.Separator className="my-1 mx-2 h-1px bg-[rgba(255,255,255,.14)]" />;

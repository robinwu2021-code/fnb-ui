// Button ← 原型 sh-btn。variant：default 墨实心（一屏只一个）· soft 主色浅底 · muted 浅灰块 · danger 浅红底红字 · danger-solid 只在不可撤销的最后一步 · link 文字
import { cva, type VariantProps } from 'class-variance-authority';
import { Slot } from '@radix-ui/react-slot';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-1.5 h-ctl px-px rounded-rb text-md font-600 whitespace-nowrap tracking-[.01em] select-none active:translate-y-px [&>svg]:w-18px [&>svg]:h-18px [&>svg]:flex-none disabled:(bg-wash text-placeholder font-500 cursor-default active:translate-y-0)',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-on hover:bg-primary-hover active:bg-primary-pressed',
        soft: 'bg-primary-tint text-primary-text hover:bg-primary-tint-hover',
        muted: 'bg-wash text-ink font-500 hover:bg-strong',
        danger: 'bg-danger-tint text-danger-text',
        'danger-solid': 'bg-danger-strong text-on-solid',
        off: 'bg-wash text-placeholder font-500 cursor-default active:translate-y-0',
        link: 'h-auto px-0 bg-transparent text-primary-text font-600 rounded-0',
      },
      size: {
        md: '',
        sm: 'hit h-ctl-sm px-[calc(var(--px)-4px)] text-sm [&>svg]:w-16px [&>svg]:h-16px',
        lg: 'h-ctl-lg px-[calc(var(--px)+8px)] text-lg',
        icon: 'w-ctl px-0',
        'icon-sm': 'hit w-ctl-sm h-ctl-sm px-0 text-sm [&>svg]:w-16px [&>svg]:h-16px',
      },
      block: { true: 'w-full btn-block' },
      split: { true: 'justify-between' },
    },
    defaultVariants: { variant: 'default', size: 'md' },
  },
);
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { asChild?: boolean }
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ className, variant, size, block, split, asChild, type = 'button', ...props }, ref) {
  const Comp = asChild ? Slot : 'button';
  return <Comp ref={ref} type={asChild ? undefined : type} className={cn(buttonVariants({ variant, size, block, split }), className)} {...props} />;
});

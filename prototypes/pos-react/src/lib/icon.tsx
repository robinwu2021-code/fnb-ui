// 图标一律 Lucide，线宽 1.75；按原型里的 kebab-case 名取（chef-hat → ChefHat），名字沿用 pos-states.js
import { icons, type LucideProps } from 'lucide-react';
import { forwardRef } from 'react';

export type IconName = string;
function pascal(name: string) { return name.split('-').map((s) => s.charAt(0).toUpperCase() + s.slice(1)).join(''); }

export const Ico = forwardRef<SVGSVGElement, LucideProps & { name: IconName }>(function Ico({ name, size = 16, strokeWidth = 1.75, ...rest }, ref) {
  const C = (icons as Record<string, React.ComponentType<LucideProps>>)[pascal(name)];
  if (!C) return <span data-missing-icon={name} style={{ width: size, height: size, display: 'inline-block' }} />;
  return <C ref={ref} size={size} strokeWidth={strokeWidth} aria-hidden {...rest} />;
});

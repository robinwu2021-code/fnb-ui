// 结单区「更多」菜单 ← 原型 OVL.menu（DropdownMenu，反色面）
import { useStore } from '@/store';
import { useT } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Menu, MenuTrigger, MenuContent, MenuItem, MenuSep } from '@/components/ui/dropdown-menu';
import { Ico } from '@/lib/icon';
export function MoreMenu() {
  const t = useT(); const dispatch = useStore((s) => s.act);
  return (
    <Menu>
      <MenuTrigger asChild><Button variant="muted" size="icon-sm" className="ml-auto" aria-label={t('order.more')}><Ico name="ellipsis" size={16} /></Button></MenuTrigger>
      <MenuContent>
        <MenuItem onSelect={() => dispatch('toast:toast.guests')}>{t('menu.guests')}</MenuItem><MenuItem onSelect={() => dispatch('ov:note')}>{t('menu.note')}</MenuItem><MenuItem onSelect={() => dispatch('ov:x3')}>{t('menu.move')}</MenuItem>
        <MenuSep />
        <MenuItem onSelect={() => dispatch('toast:toast.prebill')}>{t('menu.prebill')}</MenuItem><MenuItem onSelect={() => dispatch('batch:split')}>{t('menu.split')}</MenuItem><MenuItem onSelect={() => dispatch('ov:x3')}>{t('menu.transfer')}</MenuItem>
        <MenuSep />
        <MenuItem hint={t('menu.needAuth')} onSelect={() => dispatch('ov:x2')}>{t('menu.comp')}</MenuItem><MenuItem danger hint={t('menu.needAuth')} onSelect={() => dispatch('ov:x2')}>{t('menu.void')}</MenuItem><MenuItem disabled hint={t('menu.unpaid')}>{t('menu.clear')}</MenuItem>
        <MenuSep />
        <MenuItem hint={t('edit.openHint')} onSelect={() => dispatch('edit:on')}><Ico name="pencil-ruler" size={16} />{t('edit.open')}</MenuItem>
      </MenuContent>
    </Menu>
  );
}

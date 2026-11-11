// 设备容器：1280 × 800 的舞台，按页面宽度缩放。浮层（Dialog / Sheet / Menu / Toast）都 portal 到这个元素里，遮罩只盖设备。
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
const Ctx = createContext<HTMLElement | null>(null);
export const useDeviceEl = () => useContext(Ctx);
export function Device({ children }: { children: ReactNode }) {
  const stage = useRef<HTMLDivElement>(null), dev = useRef<HTMLDivElement>(null);
  const [el, setEl] = useState<HTMLElement | null>(null);
  const [scale, setScale] = useState(1);
  useEffect(() => {
    setEl(dev.current);
    const ro = new ResizeObserver(() => { if (stage.current) setScale(Math.min(1, stage.current.clientWidth / 1280)); });
    if (stage.current) ro.observe(stage.current);
    return () => ro.disconnect();
  }, []);
  return (
    <div ref={stage} className="relative w-full" style={{ height: 800 * scale + 20 }}>
      <div ref={dev} id="device" className="absolute left-0 top-0 w-1280px h-800px origin-top-left grid grid-rows-[56px_auto_minmax(0,1fr)] bg-canvas rounded-18px overflow-hidden shadow-[0_0_0_10px_#1C1D20] isolate" style={{ transform: `scale(${scale})` }}>
        <Ctx.Provider value={el}>{children}</Ctx.Provider>
      </div>
    </div>
  );
}

import { defineConfig } from "vite";
import uniModule from "@dcloudio/vite-plugin-uni";
import UnoCSS from "unocss/vite";

// 与 ai-shop b-app 同一写法：vite-plugin-uni 的默认导出在 ESM 下包了一层 default
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const uni = ((uniModule as any).default ?? uniModule) as () => any;

export default defineConfig({
  plugins: [uni(), UnoCSS()],
  server: { host: "0.0.0.0", port: 5280, strictPort: false },
});

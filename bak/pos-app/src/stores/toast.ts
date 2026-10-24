import { reactive } from "vue";

export const toast = reactive({ text: "", show: false });
let timer: ReturnType<typeof setTimeout> | undefined;
export function showToast(text: string) {
  toast.text = text;
  toast.show = true;
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => { toast.show = false; }, 2400);
}

import { reactive } from "vue";

export type OperationFeedbackTone = "success" | "error";

export const operationFeedback = reactive({
  visible: false,
  tone: "success" as OperationFeedbackTone,
  message: "",
  sequence: 0,
});

let hideTimer: number | undefined;

export function showOperationFeedback(tone: OperationFeedbackTone, message: string) {
  operationFeedback.tone = tone;
  operationFeedback.message = message;
  operationFeedback.visible = true;
  operationFeedback.sequence += 1;
  if (hideTimer) window.clearTimeout(hideTimer);
  hideTimer = window.setTimeout(() => {
    operationFeedback.visible = false;
  }, tone === "error" ? 5000 : 2600);
}

import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import "./styles.css";
import { explainInvalidField } from "./validation";

document.addEventListener("invalid", explainInvalidField, true);
for (const event of ["input", "change"]) {
  document.addEventListener(event, ({ target }) => {
    if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
      // 日期等字段的上下限会互相影响，任一输入变化后重新计算整张表单的提示。
      const fields = target.form ? Array.from(target.form.elements) : [target];
      for (const field of fields) {
        if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement || field instanceof HTMLTextAreaElement) {
          field.setCustomValidity("");
        }
      }
    }
  });
}

createApp(App).use(router).mount("#app");

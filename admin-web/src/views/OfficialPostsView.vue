<script setup lang="ts">
import { requireText } from "../validation";
import { onMounted, reactive, ref } from "vue";
import { Send, Pin } from "@lucide/vue";
import { api } from "../api";
import { auth } from "../auth";
const form = reactive({ body: "", sportTypeId: 0 });
const sports = ref<{ id: number; name: string }[]>([]);
const published = ref<{ id: number; moderationStatus: string } | null>(null);
const saving = ref(false); const error = ref(""); const message = ref("");
const platform = auth.principal?.role === "PLATFORM_ADMIN";
onMounted(async () => { try { sports.value = await api(platform ? "/platform/sports/list" : "/venue-admin/posts/sports"); form.sportTypeId = sports.value[0]?.id || 0; } catch (cause) { error.value = cause instanceof Error ? cause.message : "加载失败"; } });
async function publish() {
  saving.value = true; error.value = "";
  try { requireText(form.body, "动态正文", 2000); if (!form.sportTypeId) throw new Error("请选择运动类型"); published.value = await api(platform ? "/platform/posts" : "/venue-admin/posts", { method: "POST", body: JSON.stringify(form) }); message.value = published.value?.moderationStatus === "PENDING" ? "官方动态已提交审核" : "官方动态已发布"; form.body = ""; }
  catch (cause) { error.value = cause instanceof Error ? cause.message : "发布失败"; }
  finally { saving.value = false; }
}
async function pin() { if (!published.value) return; try { await api(`/platform/posts/${published.value.id}/pin?pinned=true`, { method: "POST" }); message.value = "公告已置顶"; } catch (cause) { error.value = cause instanceof Error ? cause.message : "置顶失败"; } }
</script>
<template><section class="page official-posts-page"><header class="page-header"><div><h1>官方动态</h1><p>向用户发布场馆与运动消息。</p></div></header><p v-if="error" class="error-banner" role="alert">{{ error }}</p><p v-if="message" class="saved-message" role="status">{{ message }}</p><form class="form-panel official-form" @submit.prevent="publish"><label class="field"><span>运动类型</span><select v-model.number="form.sportTypeId" required><option v-for="sport in sports" :key="sport.id" :value="sport.id">{{ sport.name }}</option></select></label><label class="field"><span>动态正文</span><textarea v-model="form.body" rows="8" maxlength="2000" required /></label><div class="row-actions"><button class="button primary" :disabled="saving"><Send :size="18" />发布</button><button v-if="platform && published" class="button secondary" type="button" @click="pin"><Pin :size="18" />置顶为公告</button></div></form></section></template>
<style scoped>.official-form { max-width: 920px; display: grid; gap: var(--space-md); }</style>

<script setup lang="ts">
import {reactive} from 'vue'
import {useRouter} from 'vue-router'
import {useWorkbench} from '../stores/workbench'
import {uid} from '../lib/domain'
import Dialog from './Dialog.vue'
import Icon from './Icon.vue'
defineProps<{open:boolean}>();const emit=defineEmits<{close:[]}>();const store=useWorkbench(),router=useRouter()
const form=reactive({title:'',brief:'',template:'SCREENCAST',budget:100,platforms:['DOUYIN','XIAOHONGSHU']})
async function submit(){const id=uid('project');if(!Number.isFinite(form.budget)||form.budget<0){store.toast('预算必须是有效的非负金额','error');return}if(await store.command({type:'CREATE_PROJECT',payload:{...form,id}},'新作品已创建，制作节点已准备好')){emit('close');router.push('/projects/'+id);form.title='';form.brief=''}}
</script>
<template><Dialog :open="open" title="从一个好想法开始" @close="emit('close')"><p class="muted form-intro">先写下你想表达的内容，细节可以在制作中逐步补全。</p><form @submit.prevent="submit" class="form-stack"><label>作品名称<span class="required">*</span><input v-model="form.title" required maxlength="100" placeholder="例如：普通人的 AI 使用手册" autofocus></label><label>最初的想法<textarea v-model="form.brief" rows="3" placeholder="想讲给谁听？希望观众看完后得到什么？"/></label><label>制作路线<select v-model="form.template"><option v-for="t in store.catalog.templates" :key="t.id" :value="t.id">{{t.name}}</option></select></label><div class="form-row"><label>项目预算（元）<input type="number" v-model.number="form.budget" min="0" max="1000000" step="0.01"></label><fieldset class="platform-field"><legend>发布平台</legend><label class="checkbox-label"><input type="checkbox" value="DOUYIN" v-model="form.platforms">抖音</label><label class="checkbox-label"><input type="checkbox" value="XIAOHONGSHU" v-model="form.platforms">小红书</label></fieldset></div><div class="form-hint"><Icon name="ShieldCheck" :size="16"/>创建项目不会调用模型、扣费或发布内容。</div><button class="btn primary full" :disabled="store.saving"><Icon name="Plus"/>创建作品</button></form></Dialog></template>

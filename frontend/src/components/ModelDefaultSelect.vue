<script setup lang="ts">
import {computed} from 'vue'
import {useWorkbench} from '../stores/workbench'
const props=withDefaults(defineProps<{scope:'GLOBAL'|'PROJECT'|'NODE'|'AGENT';label:string;projectId?:string;nodeId?:string;agentId?:string}>(),{projectId:'',nodeId:'',agentId:''})
const store=useWorkbench()
const value=computed(()=>{
 const project=store.state.projects.find(p=>p.id===props.projectId)
 const map=props.scope==='GLOBAL'?store.state.globalBindings:props.scope==='AGENT'?store.state.agentBindings[props.agentId]:props.scope==='PROJECT'?project?.bindings:project?.nodeBindings[props.nodeId]
 return map?.$default??''
})
function select(event:Event){store.command({type:'SET_DEFAULT_MODEL',payload:{scope:props.scope,projectId:props.projectId,nodeId:props.nodeId,agentId:props.agentId,modelId:(event.target as HTMLSelectElement).value}},props.scope==='GLOBAL'?'全局默认模型已保存，未覆盖的部分自动继承':'局部默认模型已保存')}
</script>
<template><label class="default-model-field">{{label}}<select :aria-label="label" :value="value" :disabled="store.saving||!store.ready" @change="select"><option value="">{{scope==='GLOBAL'?'暂不设置全局模型':'继承上级模型'}}</option><option v-for="model in store.state.models" :key="model.id" :value="model.id">{{model.name}}</option></select><small v-if="!store.state.models.length">请先在模型中心登记模型。</small></label></template>

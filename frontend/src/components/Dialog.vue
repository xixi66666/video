<script setup lang="ts">
import {ref,watch,nextTick,onMounted} from 'vue'
import Icon from './Icon.vue'
const props=defineProps<{open:boolean;title:string;wide?:boolean}>(),emit=defineEmits<{close:[]}>();const dialog=ref<HTMLDialogElement>()
async function sync(){await nextTick();if(props.open&&!dialog.value?.open)dialog.value?.showModal();else if(!props.open&&dialog.value?.open)dialog.value.close()}
watch(()=>props.open,sync);onMounted(sync)
</script>
<template><dialog ref="dialog" :class="['dialog',{'dialog-wide':wide}]" @close="emit('close')" @click="e=>{if(e.target===dialog)emit('close')}"><div class="dialog-header"><h2>{{title}}</h2><button class="icon-btn" aria-label="关闭" @click="emit('close')"><Icon name="X"/></button></div><div class="dialog-content"><slot/></div></dialog></template>

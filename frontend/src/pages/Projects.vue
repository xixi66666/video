<script setup lang="ts">
import {computed,ref} from 'vue'
import {useWorkbench} from '../stores/workbench'
import ProjectCard from '../components/ProjectCard.vue'
import Icon from '../components/Icon.vue'
const emit=defineEmits<{create:[]}>(),store=useWorkbench(),query=ref(''),filter=ref('all')
const filtered=computed(()=>store.state.projects.filter(p=>p.title.includes(query.value)&&(filter.value==='all'||(filter.value==='real'?!p.sample:p.sample))))
</script>
<template><div class="page-content"><div class="page-heading"><div><span class="page-eyebrow">你的表达，持续发生</span><h1>我的作品<span class="heading-dot">.</span></h1><p>让每一个想法，都有自己的制作现场。</p></div><button class="btn primary" @click="emit('create')"><Icon name="Plus"/>新建作品</button></div><div class="filter-bar"><div class="segmented"><button v-for="f in [{id:'all',name:'全部作品'},{id:'real',name:'我的创作'},{id:'sample',name:'示例项目'}]" :key="f.id" :class="{selected:filter===f.id}" @click="filter=f.id">{{f.name}}</button></div><div class="inline-search"><Icon name="Search" :size="16"/><input v-model="query" placeholder="搜索作品" aria-label="搜索作品"></div></div><div class="project-grid"><ProjectCard v-for="p in filtered" :key="p.id" :project="p"/><button class="new-project-tile" @click="emit('create')"><span><Icon name="Plus" :size="25"/></span><strong>下一条作品，从这里开始</strong><small>文字、录屏、实拍，选择适合你的路线。</small></button></div></div></template>

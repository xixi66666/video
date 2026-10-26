<script setup lang="ts">
import {Circle,LayoutDashboard,FolderOpen,Lightbulb,Workflow,Images,Boxes,Bot,Plug,ListTodo,CheckCheck,ChartNoAxesCombined,Settings2,Plus,ArrowUpRight,ArrowRight,ChevronRight,ChevronsUpDown,Search,Bell,Menu,X,Check,CheckCircle2,Clock3,AlertCircle,Upload,Download,FileText,Film,AudioLines,Image,SlidersHorizontal,ExternalLink,Trash2,Save,RotateCcw,Info,ShieldCheck,HardDrive,Laptop,Route,Play,Scissors,Clapperboard,Send,ArrowUp,FolderHeart,Inbox,Sparkles,Shapes,GitBranch,Pencil,ArrowLeft,Braces,UserRoundCheck,FolderSearch,PanelsTopLeft,MonitorPlay,CirclePause,ArrowDownLeft} from 'lucide-vue-next'
import {computed,type Component} from 'vue'
const props=withDefaults(defineProps<{name:string;size?:number;strokeWidth?:number}>(),{size:18,strokeWidth:1.7})
const icons:Record<string,Component>={Circle,LayoutDashboard,FolderOpen,Lightbulb,Workflow,Images,Boxes,Bot,Plug,ListTodo,CheckCheck,ChartNoAxesCombined,Settings2,Plus,ArrowUpRight,ArrowRight,ChevronRight,ChevronsUpDown,Search,Bell,Menu,X,Check,CheckCircle2,Clock3,AlertCircle,Upload,Download,FileText,Film,AudioLines,Image,SlidersHorizontal,ExternalLink,Trash2,Save,RotateCcw,Info,ShieldCheck,HardDrive,Laptop,Route,Play,Scissors,Clapperboard,Send,ArrowUp,FolderHeart,Inbox,Sparkles,Shapes,GitBranch,Pencil,ArrowLeft,Braces,UserRoundCheck,FolderSearch,PanelsTopLeft,MonitorPlay,CirclePause,ArrowDownLeft}
const icon=computed(()=>icons[props.name]??Circle)
</script>
<template><component :is="icon" :size="size" :stroke-width="strokeWidth" aria-hidden="true"/></template>

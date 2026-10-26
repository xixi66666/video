import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import {initialState,makeProject,applyCommand,resolveModel,migrateWorkspace,blankNode,type Catalog,type Model} from '../src/lib/domain.ts'
const catalog=JSON.parse(fs.readFileSync(new URL('../src/data/catalog.json',import.meta.url),'utf8')) as Catalog
test('全局模型默认继承、局部覆盖、清除覆盖以及能力不兼容回退',()=>{
 let state=initialState(catalog);const projectId=state.projects[0].id,node=catalog.nodes.find(n=>n.id==='N22')!
 state.models=['global','project','node','tts'].map(id=>({id,name:id,capabilities:[id==='tts'?'TTS':'TEXT_REASONING']} as Model))
 const bind=(scope:string,modelId:string)=>{state=applyCommand(state,{type:'SET_DEFAULT_MODEL',payload:{scope,projectId,nodeId:node.id,modelId}},catalog)}
 bind('GLOBAL','global');assert.equal(resolveModel(state,state.projects[0],node,'TEXT_REASONING').model?.id,'global')
 bind('PROJECT','project');assert.equal(resolveModel(state,state.projects[0],node,'TEXT_REASONING').model?.id,'project')
 bind('NODE','node');assert.equal(resolveModel(state,state.projects[0],node,'TEXT_REASONING').model?.id,'node')
 bind('NODE','');bind('PROJECT','');assert.equal(resolveModel(state,state.projects[0],node,'TEXT_REASONING').model?.id,'global')
 assert.equal(resolveModel(state,state.projects[0],node,'TTS').model,undefined)
 state=applyCommand(state,{type:'SET_BINDING',payload:{scope:'GLOBAL',capability:'TTS',modelId:'tts'}},catalog)
 assert.equal(resolveModel(state,state.projects[0],node,'TTS').model?.id,'tts')
 state=applyCommand(state,{type:'DELETE_MODEL',payload:{id:'global'}},catalog);assert.equal(state.globalBindings.$default,undefined)
})
test('N01 从活动流程与依赖中移除，旧产物与绑定保留，迁移幂等',()=>{
 const state=initialState(catalog),project=state.projects[0]
 project.nodes.N01={...blankNode(),draft:'历史目标',output:'历史目标',revision:3,status:'COMPLETED'}
 project.nodeBindings.N01={$default:'historical-model'};project.nodes.N02.dependencies.N01=3
 const migrated=migrateWorkspace(state),p=migrated.projects[0]
 assert.equal(p.nodes.N01,undefined);assert.equal(p.archivedNodes?.N01.output,'历史目标');assert.equal(p.archivedNodeBindings?.N01.$default,'historical-model')
 assert.equal(p.nodes.N02.dependencies.N01,undefined);assert.deepEqual(migrateWorkspace(migrated),migrated)
 assert.equal(project.nodes.N01.output,'历史目标');assert.equal(catalog.nodes[0].id,'N02');assert.ok(catalog.nodes.every(n=>!n.dependsOn.includes('N01')))
 const created=makeProject({title:'新视频'},catalog.nodes);assert.equal(Object.keys(created.nodes).length,49)
})
test('任务软删除和恢复只修改记录可见性，不删除作品产物',()=>{
 const state=initialState(catalog),project=state.projects[0]
 const planned=applyCommand(state,{type:'PLAN_PROJECT',payload:{projectId:project.id}},catalog),id=planned.tasks[0].id
 const deleted=applyCommand(planned,{type:'DELETE_TASK',payload:{id}},catalog)
 assert.ok(deleted.tasks[0].deletedAt);assert.deepEqual(deleted.projects,planned.projects)
 const restored=applyCommand(deleted,{type:'RESTORE_TASK',payload:{id}},catalog);assert.equal(restored.tasks[0].deletedAt,undefined)
 assert.throws(()=>applyCommand(state,{type:'DELETE_TASK',payload:{id:'missing'}},catalog),/不存在/)
})

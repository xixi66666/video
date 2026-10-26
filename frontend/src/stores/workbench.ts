import {computed,ref} from 'vue'
import {defineStore} from 'pinia'
import data from '../data/catalog.json'
import {applyCommand,initialState,migrateWorkspace,uid,type Catalog,type Command,type WorkspaceState,type Asset} from '../lib/domain'
export const useWorkbench=defineStore('workbench',()=>{
 const catalog=data as Catalog,state=ref<WorkspaceState>(initialState(catalog)),connection=ref<'local'|'server'>('local'),ready=ref(false),saving=ref(false),notice=ref(''),noticeKind=ref<'success'|'error'>('success')
 const key='creator-workbench-v1';let noticeTimer:ReturnType<typeof setTimeout>;let pending=Promise.resolve()
 function toast(message:string,kind:'success'|'error'='success'){notice.value=message;noticeKind.value=kind;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>notice.value='',5000)}
 async function init(){try{const response=await fetch('/api/v1/workspace',{signal:AbortSignal.timeout(1800)});if(!response.ok)throw new Error();const body=await response.json();state.value=migrateWorkspace(body.data);connection.value='server'}catch{try{const stored=localStorage.getItem(key);if(stored){const parsed=JSON.parse(stored);if(parsed.projects&&parsed.models&&typeof parsed.version==='number')state.value=migrateWorkspace(parsed)}}catch{toast('本地存储无法读取，当前使用新工作区','error')}}ready.value=true}
 async function command(cmd:Command,success='已保存'){
  const run=async()=>{saving.value=true;try{if(connection.value==='server'){const response=await fetch('/api/v1/commands',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...cmd,expectedVersion:state.value.version})});const result=await response.json();if(!response.ok)throw new Error(result.message??'保存失败');state.value=result.data}else{const next=applyCommand(state.value,cmd,catalog);localStorage.setItem(key,JSON.stringify(next));state.value=next}if(success)toast(success);return true}catch(e){toast(e instanceof Error?e.message:'操作失败，请重试','error');return false}finally{saving.value=false}}
  const result=pending.then(run);pending=result.then(()=>{});return result
 }
 const actualProjects=computed(()=>state.value.projects.filter(p=>!p.sample))
 async function addAsset(file:File,projectId=''){
  const id=uid('asset');let fileRef=''
  if(connection.value==='server'){const form=new FormData();form.append('file',file);const r=await fetch('/api/v1/files',{method:'POST',body:form});if(!r.ok){toast('文件上传失败','error');return}fileRef=(await r.json()).data.fileRef}
  else{const db=await openFiles();await new Promise<void>((resolve,reject)=>{const tx=db.transaction('files','readwrite');tx.objectStore('files').put(file,id);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error)});db.close();fileRef='indexeddb:'+id}
  const asset:Asset={id,name:file.name,type:file.type||'application/octet-stream',size:file.size,source:'人工上传',fileRef,projectId,createdAt:new Date().toISOString()};await command({type:'ADD_ASSET',payload:{asset}},'素材已保存')
 }
 async function assetUrl(asset:Asset){if(!asset.fileRef.startsWith('indexeddb:'))return asset.fileRef;const db=await openFiles();const blob=await new Promise<Blob>((resolve,reject)=>{const req=db.transaction('files').objectStore('files').get(asset.id);req.onsuccess=()=>req.result?resolve(req.result):reject(new Error('素材文件不存在'));req.onerror=()=>reject(req.error)});db.close();return URL.createObjectURL(blob)}
 function openFiles():Promise<IDBDatabase>{return new Promise((resolve,reject)=>{const req=indexedDB.open('creator-workbench-files',1);req.onupgradeneeded=()=>req.result.createObjectStore('files');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error)})}
 function exportState(){const blob=new Blob([JSON.stringify({exportedAt:new Date().toISOString(),schemaVersion:1,state:state.value},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='creator-workbench-backup.json';a.click();URL.revokeObjectURL(url);toast('工作区数据已导出；文件素材需单独备份')}
 return {catalog,state,connection,ready,saving,notice,noticeKind,actualProjects,init,command,toast,addAsset,assetUrl,exportState}
})

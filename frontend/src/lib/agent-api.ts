export interface AgentEvent {seq:number;kind:string;role:string;at:string;data:Record<string,unknown>}
export interface Candidate {body:string;fields:Record<string,unknown>;sources:string[];uncertainties:string[];fixture:boolean;executionMode:string;version:number;sha256:string}
export interface AgentRun {id:string;projectId:string;nodeId:string;state:string;createdAt:string;deletedAt?:string;mode:string;executionMode:string;dispatchAttempts?:number;dispatchError?:string;models:Record<string,{name?:string}>;limits:Record<string,number>;runtimeMessage?:string;runtime?:{status:string;error?:string;artifacts:{kind:string;version:number;content:Candidate&{verdict?:string;summary?:string;issues?:string[]}}[]};events?:AgentEvent[]}
export interface Readiness {online:boolean;liveModelsEnabled:boolean;message?:string;profiles?:{id:string;name:string;version:string;lifecycle:string;domainVerified:boolean;sha256:string;tools:string[]}[]}
export async function agentApi<T>(path='',body?:object,signal?:AbortSignal):Promise<T>{
 const response=await fetch('/api/v1/agent-runs'+path,{method:body?'POST':'GET',headers:body?{'Content-Type':'application/json'}:undefined,body:body?JSON.stringify(body):undefined,signal:signal??AbortSignal.timeout(12000)})
 const result=await response.json();if(!response.ok)throw new Error(result.message??'Agent 服务请求失败');return result.data
}

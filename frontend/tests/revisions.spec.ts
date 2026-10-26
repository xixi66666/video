import {test,expect} from '@playwright/test'
import fs from 'node:fs'
import {initialState,type Catalog,type Model} from '../src/lib/domain'
const catalog=JSON.parse(fs.readFileSync(new URL('../src/data/catalog.json',import.meta.url),'utf8')) as Catalog
test.beforeEach(async({page})=>{
 const state=initialState(catalog)
 state.models=['全局写作模型','局部编辑模型'].map((name,index)=>({id:'writer-'+index,name,provider:'验证',protocol:'CUSTOM_HTTP',baseUrl:'https://example.invalid',remoteModelId:'writer-'+index,credentialRef:'CREATOR_TEST_KEY',capabilities:['TEXT_REASONING'],parameters:{},enabled:false,verified:false} as Model))
 await page.addInitScript(state=>{if(!sessionStorage.getItem('revision-fixture-ready')){localStorage.setItem('creator-workbench-v1',JSON.stringify(state));sessionStorage.setItem('revision-fixture-ready','1')}},state)
 await page.route('**/api/v1/workspace',route=>route.abort());await page.goto('/');await expect(page.locator('.workspace-note')).toContainText('浏览器本地保存')
})
test('顶部全局模型、作品与节点继承及清除覆盖',async({page})=>{
 await page.locator('.global-model-control summary').click();await page.getByLabel('全局默认模型',{exact:true}).selectOption('writer-0');await page.locator('.global-model-control summary').click()
 await page.goto('/projects/sample_ai?node=N22');await expect(page.locator('.model-binding').filter({hasText:'LLM 语言与推理'})).toContainText('全局默认 · 全局写作模型')
 await page.getByLabel('作品默认模型',{exact:true}).selectOption('writer-1');await expect(page.locator('.model-binding').first()).toContainText('作品默认 · 局部编辑模型')
 await page.getByLabel('本节点默认模型',{exact:true}).selectOption('writer-0');await expect(page.locator('.model-binding').first()).toContainText('节点默认 · 全局写作模型')
 await page.getByLabel('本节点默认模型',{exact:true}).selectOption('');await page.getByLabel('作品默认模型',{exact:true}).selectOption('');await expect(page.locator('.model-binding').first()).toContainText('全局默认 · 全局写作模型')
 // addInitScript 只用于初始准备，当前页面内通过路由切换验证持久状态。
 await page.getByRole('link',{name:'模型中心',exact:true}).click();await expect(page.locator('.global-default-panel').getByLabel('全局默认模型',{exact:true})).toHaveValue('writer-0')
})
test('通用流程先看说明，所选作品明确决定跳转目标',async({page})=>{
 await page.goto('/workflow');await expect(page.getByLabel('流程所属作品')).toHaveValue('')
 await page.locator('.workflow-row').filter({hasText:'N22'}).click();await expect(page).toHaveURL(/\/workflow\?node=N22/);await expect(page.locator('.workflow-node-preview')).toContainText('完成标准')
 await expect(page.getByRole('button',{name:'先在上方选择作品'})).toBeDisabled()
 await page.getByLabel('流程所属作品').selectOption('sample_ai');await page.getByRole('button',{name:'在所选作品中打开此节点'}).click();await expect(page).toHaveURL(/\/projects\/sample_ai\?node=N22/)
 await expect(page.getByRole('heading',{name:'普通人的 AI 使用手册',exact:true})).toBeVisible();await expect(page.locator('.node-item')).toHaveCount(49);await expect(page.locator('.node-sidebar')).not.toContainText('明确副业目标')
 await page.getByRole('link',{name:'查看此作品的完整流程'}).click();await expect(page.getByLabel('流程所属作品')).toHaveValue('sample_ai')
 await page.getByLabel('流程所属作品').selectOption('sample_studio');await page.locator('.workflow-row').filter({hasText:'N08'}).click();await expect(page).toHaveURL(/\/projects\/sample_studio\?node=N08/)
 await page.goto('/workflow?node=N22');await expect(page.getByLabel('流程所属作品')).toHaveValue('');fs.mkdirSync('ui-artifacts',{recursive:true});await page.screenshot({path:'ui-artifacts/workflow-context.png',fullPage:false,animations:'disabled'})
})
test('删除任务可取消、可恢复，作品与节点仍保留；角色页说明实际状态',async({page})=>{
 await page.goto('/projects/sample_ai');await page.getByRole('button',{name:'检查下一步',exact:true}).click();await page.getByRole('link',{name:'任务中心',exact:true}).click();await expect(page.locator('.task-list article')).toHaveCount(1)
 await page.getByRole('button',{name:'删除任务：制作计划检查'}).click();await page.getByRole('button',{name:'保留任务'}).click();await expect(page.locator('.task-list article')).toHaveCount(1)
 await page.getByRole('button',{name:'删除任务：制作计划检查'}).click();await page.getByRole('button',{name:'删除任务记录',exact:true}).click();await expect(page.locator('.task-list article')).toHaveCount(0)
 await page.getByRole('button',{name:'已删除',exact:true}).click();await expect(page.locator('.task-list article')).toHaveCount(1);await page.getByRole('button',{name:'恢复任务',exact:true}).click();await expect(page.locator('.task-list article')).toHaveCount(0)
 await page.getByRole('button',{name:'全部任务',exact:true}).click();await expect(page.locator('.task-list article')).toHaveCount(1)
 await page.getByRole('link',{name:'Agent 团队',exact:true}).click();await expect(page.locator('.info-strip')).toContainText('当前使用测试模型');await expect(page.locator('.agent-card')).toHaveCount(19);await expect(page.getByRole('link',{name:'阅读实际代码与完整协作设计'})).toHaveAttribute('href','/guides/agent-runtime.md')
})

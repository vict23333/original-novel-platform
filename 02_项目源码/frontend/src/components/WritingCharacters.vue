<script setup>
import { computed,reactive,shallowRef,watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api } from '../session'
import NameTools from './NameTools.vue'
const props=defineProps({novelId:Number,characters:Array})
const emit=defineEmits(['changed','dirty','busy'])
const empty=()=>({id:null,name:'',aliases:'',storyRole:'',profile:'',version:0})
const form=reactive(empty()),baseline=shallowRef(JSON.stringify(empty())),busy=shallowRef(false),error=shallowRef(''),message=shallowRef('')
const dirty=computed(()=>JSON.stringify(form)!==baseline.value)
watch(dirty,value=>emit('dirty',value),{immediate:true})
async function choose(item){
  if(dirty.value)try{await ElMessageBox.confirm('当前角色有未保存的修改，是否放弃后切换？','切换角色',{confirmButtonText:'放弃并切换',cancelButtonText:'继续编辑'})}catch{return}
  Object.assign(form,item||empty());baseline.value=JSON.stringify(form);error.value='';message.value=''
}
async function save(){
  if(busy.value)return
  busy.value=true;emit('busy',true);error.value='';message.value=''
  try{const fresh=!form.id;const data=await api(`/author/novels/${props.novelId}/writing/characters${fresh?'':`/${form.id}`}`,{method:fresh?'POST':'PUT',body:{name:form.name,aliases:form.aliases,storyRole:form.storyRole,profile:form.profile,version:form.version}});emit('changed',fresh?[...props.characters,data]:props.characters.map(c=>c.id===data.id?data:c));Object.assign(form,data);baseline.value=JSON.stringify(form);message.value='角色卡已保存'}catch(e){error.value=e.message}finally{busy.value=false;emit('busy',false)}
}
async function remove(){
  try{await ElMessageBox.confirm('删除这张角色卡？已写正文保留，后续生成将不再引用它。','删除角色卡',{confirmButtonText:'删除角色卡',cancelButtonText:'保留',type:'warning'})}catch{return}
  busy.value=true;emit('busy',true);error.value=''
  try{await api(`/author/novels/${props.novelId}/writing/characters/${form.id}?version=${form.version}`,{method:'DELETE'});emit('changed',props.characters.filter(c=>c.id!==form.id));Object.assign(form,empty());baseline.value=JSON.stringify(form);message.value='角色卡已删除'}catch(e){error.value=e.message}finally{busy.value=false;emit('busy',false)}
}
</script>
<template>
  <div class="character-workspace"><aside class="character-list"><el-button :disabled="busy" @click="choose(null)">＋ 新建角色</el-button><p v-if="!characters.length" class="muted">先建立主角；不知道如何起名时可使用右侧取名工具。</p><button v-for="item in characters" :key="item.id" :class="{selected:form.id===item.id}" :disabled="busy" @click="choose(item)"><strong>{{ item.name }}</strong><small>{{ item.storyRole||'尚未填写身份' }}</small></button></aside>
    <form class="character-editor" @submit.prevent="save"><div class="section-heading"><h2>{{ form.id?'编辑角色':'新建角色' }}</h2><span class="muted">{{ characters.length }} / 100 张角色卡</span></div><label for="character-name">姓名</label><input id="character-name" v-model="form.name" required maxlength="50" :disabled="busy" /><NameTools :existing="characters.map(c=>c.name)" :disabled="busy" @choose="form.name=$event" /><div class="character-fields"><div><label for="character-aliases">别名 / 称呼</label><input id="character-aliases" v-model="form.aliases" maxlength="100" :disabled="busy" /></div><div><label for="character-role">故事身份</label><input id="character-role" v-model="form.storyRole" maxlength="100" :disabled="busy" placeholder="例如：主角、对手、引路人" /></div></div><label for="character-profile">人物设定</label><textarea id="character-profile" v-model="form.profile" maxlength="1000" rows="7" :disabled="busy" placeholder="性格、动机、说话特点、与其他人的关系，以及不能改变的事实…" /><div class="character-actions"><el-button type="primary" native-type="submit" :loading="busy">保存角色卡</el-button><el-button v-if="form.id" type="danger" plain :disabled="busy" @click="remove">删除角色卡</el-button><span class="muted" role="status">{{ dirty?'有未保存的修改':message }}</span></div><el-alert v-if="error" :title="error" type="error" :closable="false" /></form>
  </div>
</template>
<style scoped>
.character-workspace{display:grid;grid-template-columns:210px minmax(0,1fr);gap:22px}.character-list,.character-editor{padding:22px;border:1px solid var(--line);border-radius:10px;background:white;min-width:0}.character-list{align-self:start;max-height:600px;overflow:auto}.character-list>button:not(.el-button){display:block;width:100%;text-align:left;padding:14px;border:0;border-bottom:1px solid var(--line);background:white}.character-list small{display:block;color:var(--muted);margin-top:6px}.character-list .selected{background:#edf6f0!important}.character-fields{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:16px}.character-actions{display:flex;flex-wrap:wrap;align-items:center;gap:12px;margin-top:20px}@media(max-width:700px){.character-workspace,.character-fields{grid-template-columns:1fr}.character-list{max-height:250px;overflow:auto}}
.character-editor input,.character-editor textarea{display:block;width:100%;padding:12px 14px;min-height:44px;border:1px solid #bdcbc3;border-radius:7px;background:#fff;color:var(--ink);line-height:1.8}.character-editor textarea{resize:vertical}.character-list strong{overflow-wrap:anywhere}
</style>

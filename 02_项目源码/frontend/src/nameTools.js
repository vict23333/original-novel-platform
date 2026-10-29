const surnames = ['林','沈','顾','江','苏','许','陆','温','叶','秦','白','程']
const givenNames = {
  modern: ['予安','知夏','景川','星禾','清越','以宁','明远','书言','向晚','时雨','嘉树','望舒'],
  ancient: ['怀瑾','云舟','听澜','长宁','照月','疏桐','临渊','若衡','知微','清商','见山','归鸿'],
  fantasy: ['烬羽','逐星','霜序','问尘','玄音','照夜','渡川','流光','听雪','无涯','星阑','青冥'],
}
const beginnings = ['清','望','落','云','长','归','听','隐','寒','浮','临','远']
const endings = { place: ['溪镇','月城','风渡','山谷','云港','水巷','石原','雪岭'], faction: ['云阁','星社','风盟','山院','月堂','川会','书局','行会'] }
export function generateNames({kind='person',style='ancient',existing=[],count=8,random=Math.random}={}) {
  if(!['person','place','faction'].includes(kind) || !Object.hasOwn(givenNames,style)) return []
  const pool=kind==='person' ? surnames.flatMap(s=>givenNames[style].map(n=>s+n)) : beginnings.flatMap(s=>endings[kind].map(n=>s+n))
  for(let i=pool.length-1;i>0;i--){const j=Math.floor(Math.max(0,Math.min(.999999,random()))*(i+1));[pool[i],pool[j]]=[pool[j],pool[i]]}
  const taken=new Set(existing.map(n=>n.trim()))
  return [...new Set(pool)].filter(n=>!taken.has(n)).slice(0,Math.max(0,Math.min(12,count)))
}

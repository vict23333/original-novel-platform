"""通过本地业务 API 导入原创演示数据；进度与随机账号凭据仅保存在 .runtime。"""
import hashlib
import json
from pathlib import Path
import secrets
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.runtime'
CATALOG = ROOT / 'database/demo-catalog.json'
STATE = RUNTIME / 'demo-catalog-state.local.json'
BASE = 'http://127.0.0.1:8081/api'


def call(path, token=None, body=None, method=None, allowed=(200, 201, 204)):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, headers=headers,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8'),
        method=method or ('GET' if body is None else 'POST'))
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as exc:
        status, raw = exc.code, exc.read()
    if status not in allowed:
        # 不输出请求体、令牌、完整服务响应或账号凭据。
        raise RuntimeError(f'{req.method} {path}: HTTP {status}; import stopped, existing data retained')
    return json.loads(raw) if raw else None


def main():
    settings = json.loads((RUNTIME / 'settings.local.json').read_text(encoding='utf-8-sig'))
    raw = CATALOG.read_bytes()
    catalog = json.loads(raw)
    digest = hashlib.sha256(raw).hexdigest()
    state = json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {
        'dataset': catalog['dataset'], 'digest': digest, 'authors': {}, 'books': {}}
    if state['dataset'] != catalog['dataset'] or state['digest'] != digest:
        raise RuntimeError('Catalog changed after import. Review changes before using another dataset.')

    def persist():
        temporary = STATE.with_suffix('.tmp')
        temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
        temporary.replace(STATE)

    def login(username, password):
        return call('/auth/login', body={'username': username, 'password': password})['accessToken']

    admin = login(settings['adminUsername'], settings['adminPassword'])
    categories = {item['name']: item['id'] for item in call('/public/categories')}
    author_tokens = {}
    added_books = added_chapters = 0

    def approve(kind, item, author):
        if item['takenDown'] or item['reviewStatus'] == 'REJECTED':
            raise RuntimeError('Demo content was moderated after creation; refusing to overwrite that decision.')
        if item['reviewStatus'] == 'DRAFT':
            item = call(f'/author/{kind}/{item["id"]}/submit', author, {'version': item['version']})
        if item['reviewStatus'] == 'PENDING':
            item = call(f'/admin/{kind}/{item["id"]}/review', admin,
                {'version': item['version'], 'action': 'APPROVE', 'reason': '本地原创演示数据初始化'})
        if item['reviewStatus'] != 'APPROVED':
            raise RuntimeError('Unexpected content state; import stopped.')
        return item

    for book in catalog['books']:
        category = book['category']
        if category not in author_tokens:
            if category not in state['authors']:
                state['authors'][category] = {'username': f'demo_writer_{categories[category]:02d}',
                    'password': secrets.token_urlsafe(24), 'registered': False}
                persist()
            author = state['authors'][category]
            if not author['registered']:
                call('/auth/register', body={'username': author['username'], 'password': author['password'],
                    'nickname': book['penName'] + '（演示）', 'role': 'AUTHOR'}, allowed=(201, 409))
            # 同名账号存在但密码不匹配时停止，绝不覆盖已有用户。
            author_tokens[category] = login(author['username'], author['password'])
            author['registered'] = True
            persist()
        token = author_tokens[category]
        entry = state['books'].setdefault(book['key'], {'chapters': {}})
        payload = {'title': book['title'], 'introduction': book['intro'], 'categoryId': categories[category],
                   'serialStatus': book['status'], 'version': 0}
        if 'id' not in entry:
            existing = []
            page = 1
            while True:
                result = call(f'/author/novels?page={page}&size=50', token)
                existing.extend(item for item in result['items'] if item['title'] == book['title'])
                if page * 50 >= result['total']:
                    break
                page += 1
            if len(existing) > 1:
                raise RuntimeError('Ambiguous demo title; inspect the author workspace before retrying.')
            item = existing[0] if existing else call('/author/novels', token, payload)
            added_books += int(not existing)
            entry['id'] = item['id']
            persist()
        item = call(f'/author/novels/{entry["id"]}', token)
        if any(item[key] != payload[key] for key in ['title', 'introduction', 'categoryId', 'serialStatus']):
            raise RuntimeError('Demo work has been edited; refusing to replace user changes.')
        approve('novels', item, token)
        for number, chapter in enumerate(book['chapters'], start=1):
            body = '\n\n'.join(chapter['paragraphs'])
            key = str(number)
            if key not in entry['chapters']:
                existing = call(f'/author/novels/{entry["id"]}/chapters?size=50', token)['items']
                matches = [c for c in existing if c['chapterNumber'] == number]
                created = matches[0] if matches else call(f'/author/novels/{entry["id"]}/chapters', token,
                    {'title': chapter['title'], 'content': body, 'chapterNumber': number, 'version': 0})
                added_chapters += int(not matches)
                entry['chapters'][key] = created['id']
                persist()
            item = call(f'/author/chapters/{entry["chapters"][key]}', token)
            if item['title'] != chapter['title'] or item['content'] != body or item['chapterNumber'] != number:
                raise RuntimeError('Demo chapter has been edited; refusing to replace user changes.')
            approve('chapters', item, token)
        print(f'Ready: {book["title"]} ({len(book["chapters"])} chapters)')
    print(json.dumps({'books': len(state['books']), 'addedBooks': added_books, 'addedChapters': added_chapters}))


if __name__ == '__main__':
    main()

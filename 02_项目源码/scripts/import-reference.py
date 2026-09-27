"""导入本机已整理的参考书库；数据、映射与随机账号凭据只保存在 .runtime。"""
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path
import secrets
import threading
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / '.runtime'
CATALOG = RUNTIME / 'reference-catalog.json'
STATE = RUNTIME / 'reference-import'
BASE = 'http://127.0.0.1:8081/api'


def call(path, token=None, body=None, method=None, allowed=(200, 201, 204)):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(BASE + path, headers=headers, method=method or ('GET' if body is None else 'POST'),
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8'))
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status not in allowed:
        raise RuntimeError(f'{request.method} {path}: HTTP {status}; import stopped, progress retained')
    return json.loads(raw) if raw else None


def write(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(path)


def login(username, password):
    return call('/auth/login', body={'username': username, 'password': password})['accessToken']


def all_items(path, token):
    items, page = [], 1
    while True:
        result = call(f'{path}?page={page}&size=50', token)
        items.extend(result['items'])
        if page * 50 >= result['total']:
            return items
        page += 1


def main():
    raw = CATALOG.read_bytes()
    catalog, digest = json.loads(raw), hashlib.sha256(raw).hexdigest()
    books = catalog['books']
    # 先验证整份数据，正文超长须在整理阶段按自然段切分，不能导入时静默截断。
    if len({book['key'] for book in books}) != len(books):
        raise ValueError('Duplicate source book keys')
    for book in books:
        if not book['key'].isdigit() or not book['chapters'] or len(book['chapters']) > 100000:
            raise ValueError('Invalid source book')
        for field, limit in [('title', 50), ('intro', 1000)]:
            if not book[field].strip() or len(book[field].encode('utf-16-le')) // 2 > limit:
                raise ValueError('Invalid book text length')
        if len({c['key'] for c in book['chapters']}) != len(book['chapters']):
            raise ValueError('Duplicate source chapter keys')
        for chapter in book['chapters']:
            for field, limit in [('title', 100), ('content', 20000)]:
                if not chapter[field].strip() or len(chapter[field].encode('utf-16-le')) // 2 > limit:
                    raise ValueError('Invalid chapter text length')
    STATE.mkdir(exist_ok=True)
    manifest_path = STATE / 'manifest.local.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {
        'dataset': catalog['dataset'], 'digest': digest, 'source': catalog['source'], 'authors': {}}
    if manifest['dataset'] != catalog['dataset'] or manifest['digest'] != digest:
        raise RuntimeError('Dataset changed; review before importing a different version')
    settings = json.loads((RUNTIME / 'settings.local.json').read_text(encoding='utf-8-sig'))
    admin = login(settings['adminUsername'], settings['adminPassword'])
    categories = {row['name']: row['id'] for row in call('/public/categories')}
    if any(book['category'] not in categories for book in books):
        raise ValueError('Source category has no reviewed mapping')
    tokens = {}
    for author_name in dict.fromkeys(book['sourceAuthor'] for book in books):
        if author_name not in manifest['authors']:
            manifest['authors'][author_name] = {
                'username': 'ref_' + hashlib.sha256(author_name.encode()).hexdigest()[:12],
                'password': secrets.token_urlsafe(24), 'registered': False}
            write(manifest_path, manifest)
        account = manifest['authors'][author_name]
        if not account['registered']:
            call('/auth/register', body={'username': account['username'], 'password': account['password'],
                'nickname': author_name[:22] + '（参考导入）', 'role': 'AUTHOR'}, allowed=(201, 409))
        # 重名但密码不匹配会停下，不覆盖现有账号。
        tokens[author_name] = login(account['username'], account['password'])
        account['registered'] = True
        write(manifest_path, manifest)
    counters = {'books': 0, 'chapters': 0, 'addedBooks': 0, 'addedChapters': 0}
    counter_lock = threading.Lock()

    def approve(kind, item, author):
        if item['takenDown'] or item['reviewStatus'] == 'REJECTED':
            raise RuntimeError('Content was moderated; refusing to replace the decision')
        if item['reviewStatus'] == 'DRAFT':
            item = call(f'/author/{kind}/{item["id"]}/submit', author, {'version': item['version']})
        if item['reviewStatus'] == 'PENDING':
            item = call(f'/admin/{kind}/{item["id"]}/review', admin,
                {'version': item['version'], 'action': 'APPROVE', 'reason': '本地参考数据迁移：原作者与来源见作品简介'})
        if item['reviewStatus'] != 'APPROVED':
            raise RuntimeError('Unexpected content state')

    def import_book(book):
        author = tokens[book['sourceAuthor']]
        path = STATE / (book['key'] + '.json')
        state = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'chapters': {}}
        payload = {'title': book['title'], 'introduction': book['intro'], 'categoryId': categories[book['category']],
            'serialStatus': book['status'], 'version': 0}
        if 'id' not in state:
            existing = [row for row in all_items('/author/novels', author) if row['title'] == book['title']]
            if len(existing) > 1:
                raise RuntimeError('Ambiguous duplicate title')
            item = existing[0] if existing else call('/author/novels', author, payload)
            state['id'] = item['id']
            with counter_lock:
                counters['addedBooks'] += int(not existing)
            write(path, state)
        item = call(f'/author/novels/{state["id"]}', author)
        if any(item[field] != payload[field] for field in ['title', 'introduction', 'categoryId', 'serialStatus']):
            raise RuntimeError('Work has been edited; refusing to overwrite changes')
        if state.get('complete'):
            # 已完成的章节不重新送审，保留后续编辑或管理处理；只检查没有映射遗漏。
            if set(state['chapters']) != {c['key'] for c in book['chapters']}:
                raise RuntimeError('Incomplete source mapping')
            with counter_lock:
                counters['books'] += 1
                counters['chapters'] += len(book['chapters'])
            return
        existing_chapters = {c['chapterNumber']: c for c in all_items(f'/author/novels/{state["id"]}/chapters', author)}
        for number, source in enumerate(book['chapters'], 1):
            key = source['key']
            entry = state['chapters'].get(key)
            if entry and entry.get('complete'):
                continue
            if entry is None:
                item = existing_chapters.get(number)
                if item is None:
                    item = call(f'/author/novels/{state["id"]}/chapters', author,
                        {'chapterNumber': number, 'title': source['title'], 'content': source['content'], 'version': 0})
                    with counter_lock:
                        counters['addedChapters'] += 1
                entry = state['chapters'][key] = {'id': item['id']}
                write(path, state)
            item = call(f'/author/chapters/{entry["id"]}', author)
            if item['chapterNumber'] != number or item['title'] != source['title'] or item['content'] != source['content']:
                raise RuntimeError('Chapter has been edited; refusing to overwrite changes')
            approve('chapters', item, author)
            entry['complete'] = True
            write(path, state)
        # 章节迁移完毕后再公开作品，避免读者打开尚未完成的半本目录。
        approve('novels', call(f'/author/novels/{state["id"]}', author), author)
        state['complete'] = True
        write(path, state)
        with counter_lock:
            counters['books'] += 1
            counters['chapters'] += len(book['chapters'])
            print(json.dumps(counters), flush=True)

    # 四个独立作品并行；同一作品内部新增、送审和审核始终串行。
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = [executor.submit(import_book, book) for book in books]
        for future in as_completed(futures):
            future.result()
    write(STATE / 'last-run.json', counters)
    print(json.dumps(counters), flush=True)


if __name__ == '__main__':
    main()

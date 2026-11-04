# -*- coding: utf-8 -*-
# Archive.org 公开影视（Python 蜘蛛）
# 数据来自 archive.org 官方 advancedsearch / metadata 公开接口，
# 内容为公有领域与 CC 开放授权作品。用于测试播放器 Python 蜘蛛引擎与接口链路。
import re
from urllib.parse import quote

from base.spider import Spider as BaseSpider


class Spider(BaseSpider):
    HOST = 'https://archive.org'
    CLASSES = [
        {'type_id': 'opensource_movies', 'type_name': '开放电影'},
        {'type_id': 'feature_films', 'type_name': '公有领域长片'},
        {'type_id': 'animationandcartoons', 'type_name': '经典动画'},
        {'type_id': 'classic_tv', 'type_name': '经典电视剧'},
    ]

    def getName(self):
        return 'ArchiveOrg'

    def init(self, extend=''):
        pass

    def _api(self, path):
        rsp = self.fetch(self.HOST + path, headers={'User-Agent': 'Mozilla/5.0'}, timeout=15)
        return rsp.json()

    def _search_url(self, q, pg, rows=24):
        fl = '&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year'
        return '/advancedsearch.php?q=' + quote(q) + fl + \
            '&sort%5B%5D=downloads+desc&rows=' + str(rows) + '&page=' + str(pg) + '&output=json'

    def _to_vod(self, doc):
        return {
            'vod_id': doc.get('identifier'),
            'vod_name': doc.get('title') or doc.get('identifier'),
            'vod_pic': self.HOST + '/services/img/' + doc.get('identifier'),
            'vod_year': str(doc.get('year') or ''),
            'vod_remarks': 'Archive.org',
        }

    def homeContent(self, filter):
        return {'class': self.CLASSES, 'filters': {}}

    def homeVideoContent(self):
        data = self._api(self._search_url('collection:opensource_movies AND mediatype:movies', 1))
        docs = (data.get('response') or {}).get('docs') or []
        return {'list': [self._to_vod(d) for d in docs]}

    def categoryContent(self, tid, pg, filter, extend):
        page = int(pg) if str(pg).isdigit() else 1
        data = self._api(self._search_url('collection:' + tid + ' AND mediatype:movies', page))
        resp = data.get('response') or {}
        docs = resp.get('docs') or []
        total = resp.get('numFound') or len(docs)
        return {
            'page': page,
            'pagecount': max(1, -(-total // 24)),
            'limit': 24,
            'total': total,
            'list': [self._to_vod(d) for d in docs],
        }

    def detailContent(self, ids):
        mid = ids[0]
        meta = self._api('/metadata/' + mid)
        files = []
        for f in meta.get('files') or []:
            name = f.get('name') or ''
            size = int(f.get('size') or 0)
            if re.search(r'\.(mp4|m4v|mkv|ogv|webm)$', name, re.I) and size > 5 * 1024 * 1024:
                files.append((size, name))
        files.sort(reverse=True)
        eps = []
        for i, (size, name) in enumerate(files[:30]):
            label = '正片' if i == 0 else '备选' + str(i)
            eps.append(label + '$' + self.HOST + '/download/' + mid + '/' + quote(name))
        m = meta.get('metadata') or {}
        content = re.sub(r'<[^>]+>', '', m.get('description') or '')[:200]
        vod = {
            'vod_id': mid,
            'vod_name': m.get('title') or mid,
            'vod_pic': self.HOST + '/services/img/' + mid,
            'vod_year': str(m.get('year') or m.get('date') or ''),
            'vod_content': content,
            'vod_play_from': '直链',
            'vod_play_url': '#'.join(eps),
        }
        return {'list': [vod]}

    def playerContent(self, flag, id, vipFlags):
        return {'parse': 0, 'playUrl': '', 'url': id, 'header': {}}

    def searchContent(self, key, quick, pg='1'):
        data = self._api(self._search_url('mediatype:movies AND title:(' + key + ')', 1))
        docs = (data.get('response') or {}).get('docs') or []
        return {'list': [self._to_vod(d) for d in docs]}

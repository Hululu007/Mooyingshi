// Archive.org 公开影视（JS 蜘蛛）
// 数据来自 archive.org 官方 advancedsearch / metadata 公开接口，
// 内容为公有领域与 CC 开放授权作品。用于测试播放器 JS 蜘蛛引擎与接口链路。
const HOST = 'https://archive.org';
const CLASSES = [
    { type_id: 'opensource_movies', type_name: '开放电影' },
    { type_id: 'feature_films', type_name: '公有领域长片' },
    { type_id: 'animationandcartoons', type_name: '经典动画' },
    { type_id: 'classic_tv', type_name: '经典电视剧' }
];

function api(path) {
    const res = req(HOST + path, { headers: { 'User-Agent': 'Mozilla/5.0' } });
    return JSON.parse(res.content);
}

function toVod(doc) {
    return {
        vod_id: doc.identifier,
        vod_name: doc.title || doc.identifier,
        vod_pic: HOST + '/services/img/' + doc.identifier,
        vod_year: (doc.year || '') + '',
        vod_remarks: 'Archive.org'
    };
}

function searchUrl(q, pg, rows) {
    const fl = '&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year';
    return '/advancedsearch.php?q=' + encodeURIComponent(q) + fl +
        '&sort%5B%5D=downloads+desc&rows=' + rows + '&page=' + pg + '&output=json';
}

async function init(cfg) {
}

async function home(filter) {
    return JSON.stringify({ class: CLASSES, filters: {} });
}

async function homeVod() {
    const data = api(searchUrl('collection:opensource_movies AND mediatype:movies', 1, 24));
    const docs = (data.response && data.response.docs) || [];
    return JSON.stringify({ list: docs.map(toVod) });
}

async function category(tid, pg, filter, extend) {
    const page = parseInt(pg) || 1;
    const data = api(searchUrl('collection:' + tid + ' AND mediatype:movies', page, 24));
    const resp = data.response || {};
    const docs = resp.docs || [];
    const total = resp.numFound || docs.length;
    return JSON.stringify({
        page: page,
        pagecount: Math.max(1, Math.ceil(total / 24)),
        limit: 24,
        total: total,
        list: docs.map(toVod)
    });
}

async function detail(id) {
    const meta = api('/metadata/' + id);
    const files = (meta.files || []).filter(function (f) {
        return /\.(mp4|m4v|mkv|ogv|webm)$/i.test(f.name) && (parseInt(f.size) || 0) > 5 * 1024 * 1024;
    });
    files.sort(function (a, b) { return (parseInt(b.size) || 0) - (parseInt(a.size) || 0); });
    const eps = files.slice(0, 30).map(function (f, i) {
        return (i === 0 ? '正片' : '备选' + i) + '$' + HOST + '/download/' + id + '/' + encodeURIComponent(f.name);
    });
    const m = meta.metadata || {};
    const vod = {
        vod_id: id,
        vod_name: m.title || id,
        vod_pic: HOST + '/services/img/' + id,
        vod_year: (m.year || m.date || '') + '',
        vod_content: (m.description || '').replace(/<[^>]+>/g, '').slice(0, 200),
        vod_play_from: '直链',
        vod_play_url: eps.join('#')
    };
    return JSON.stringify({ list: [vod] });
}

async function play(flag, id, flags) {
    return JSON.stringify({ parse: 0, url: id });
}

async function search(wd, quick) {
    const data = api(searchUrl('mediatype:movies AND title:(' + wd + ')', 1, 24));
    const docs = (data.response && data.response.docs) || [];
    return JSON.stringify({ list: docs.map(toVod) });
}

export function __jsEvalReturn() {
    return {
        init: init,
        home: home,
        homeVod: homeVod,
        category: category,
        detail: detail,
        play: play,
        search: search
    };
}

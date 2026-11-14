"""Local read-only FIRMS explorer: DuckDB over existing Parquet, no duplicated data."""
import argparse
import csv
import io
import json
import math
import os
import re
from datetime import date, timedelta
from functools import lru_cache
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import duckdb

ROOT = Path(__file__).resolve().parents[1]
DATA = Path(os.environ.get('CHRONOS_DATA_DIR', ROOT.parents[1] / 'armonizacion')).resolve()
FIRST, LAST = '2003-01-01', '2025-12-31'
VIIRS_FIRST = '2012-01-20'


def scalar(value):
    if value == '': return None
    if value in ('True', 'False'): return value == 'True'
    try:
        n = float(value)
        return n if math.isfinite(n) else None
    except ValueError: return value


@lru_cache(maxsize=1)
def source_daily():
    with (DATA / 'resultados/calendario_diario.csv').open() as f:
        days = {r['date_utc']: {k: scalar(v) for k,v in r.items()} for r in csv.DictReader(f)}
    with (DATA / 'calibracion/serie_calibrada_2012_2025.csv').open() as f:
        for r in csv.DictReader(f): days[r['date_utc']].update({k: scalar(v) for k,v in r.items()})
    return days


def connection():
    db = duckdb.connect()
    db.execute("SET threads=2; SET memory_limit='2GB'; SET TimeZone='UTC'")
    path = str(DATA / 'resultados/vegetacion_nominal_alta.parquet').replace("'", "''")
    db.execute(f"CREATE VIEW detections AS SELECT * FROM read_parquet('{path}')")
    return db


def arguments(query):
    start, end = query.get('from', [FIRST])[0], query.get('to', [LAST])[0]
    if not all(re.fullmatch(r'\d{4}-\d{2}-\d{2}', v) for v in (start,end)):
        raise ValueError('Las fechas deben usar YYYY-MM-DD.')
    a,b = date.fromisoformat(start), date.fromisoformat(end)
    if a > b: raise ValueError('La fecha inicial debe ser anterior a la final.')
    if start < FIRST or end > LAST: raise ValueError('El archivo cubre 2003–2025. No contiene datos de 2026.')
    metric = query.get('metric', ['modis'])[0]
    if metric not in ('modis','viirs','union','calibrated'): raise ValueError('Serie desconocida.')
    bbox = None
    if query.get('bbox', [''])[0]:
        bbox = tuple(float(v) for v in query['bbox'][0].split(','))
        if len(bbox)!=4 or not all(math.isfinite(v) for v in bbox): raise ValueError('La caja requiere oeste,sur,este,norte.')
        w,s,e,n=bbox
        if not (-180 <= w < e <= 180 and -90 <= s < n <= 90): raise ValueError('Coordenadas o límites inválidos.')
    if bbox and metric=='calibrated': raise ValueError('La calibración se validó únicamente para la extensión completa de DSv2.')
    return start,end,metric,bbox


def where(start,end,bbox):
    sql='date_utc BETWEEN ? AND ?'; params=[start,end]
    if bbox:
        w,s,e,n=bbox
        sql+=' AND longitude BETWEEN ? AND ? AND latitude BETWEEN ? AND ?'; params += [w,e,s,n]
    return sql,params


def clean_daily(start,end,bbox):
    originals=source_daily()
    rows=[dict(v) for k,v in originals.items() if start<=k<=end]
    if bbox:
        condition,params=where(start,end,bbox)
        with connection() as db:
            result=db.execute(f'''WITH g AS (
                SELECT date_utc,cell_id,count(*) FILTER(WHERE sensor='MODIS') m,
                count(*) FILTER(WHERE sensor='VIIRS') v FROM detections WHERE {condition} GROUP BY ALL)
                SELECT date_utc,sum(m),sum(v),count(*) FILTER(WHERE m>0),count(*) FILTER(WHERE v>0),count(*),count(*) FILTER(WHERE m>0 AND v>0)
                FROM g GROUP BY date_utc''',params).fetchall()
        bydate={r[0]:r[1:] for r in result}
        keys=['modis_detections','viirs_detections','active_cells_modis','active_cells_viirs','active_cells_union','active_cells_both']
        for row in rows:
            values=bydate.get(row['date_utc'],[0]*6)
            row.update(dict(zip(keys,values)))
            if row['date_utc']<VIIRS_FIRST:
                row['active_cells_viirs']=row['viirs_detections']=None
            # Calibration never applies to a subset of the original area.
            for k in ['modis_equivalent_cells','modis_pi90_low','modis_pi90_high','eligible_modis','viirs_input_usable']:
                row[k]=None
    for row in rows:
        for key in ['modis_equivalent_cells','modis_pi90_low','modis_pi90_high']:
            row.setdefault(key,None)
    return rows


@lru_cache(maxsize=32)
def calendar(start,end,metric,bbox):
    rows=clean_daily(start,end,bbox)
    field={'modis':'active_cells_modis','viirs':'active_cells_viirs','union':'active_cells_union','calibrated':'modis_equivalent_cells'}[metric]
    buckets={}
    for r in rows:
        key=r['date_utc'][:7]
        bucket=buckets.setdefault(key,{'year':int(key[:4]),'month':int(key[5:])-1,'value':None,'available_days':0,'selected_days':0,'modis_detections':0,'viirs_detections':0,'paired_days':0,'paired_modis':0,'paired_viirs':0,'paired_calibrated':0})
        bucket['selected_days']+=1
        if r.get(field) is not None:
            bucket['value']=(bucket['value'] or 0)+r[field];bucket['available_days']+=1
        bucket['modis_detections']+=r.get('modis_detections') or 0
        bucket['viirs_detections']+=r.get('viirs_detections') or 0
        if not bbox and r.get('eligible_modis') and r.get('modis_equivalent_cells') is not None:
            bucket['paired_days']+=1
            bucket['paired_modis']+=r['active_cells_modis']
            bucket['paired_viirs']+=r['active_cells_viirs']
            bucket['paired_calibrated']+=r['modis_equivalent_cells']
    for b in buckets.values():
        import calendar as cal
        b['calendar_days']=cal.monthrange(b['year'],b['month']+1)[1]
        b['partial']=b['available_days']<b['calendar_days']
    return {'from':start,'to':end,'metric':metric,'bbox':bbox,'months':list(buckets.values()),'daily':rows,'coverage_known':False}


@lru_cache(maxsize=32)
def map_data(start,end,metric,bbox):
    if (date.fromisoformat(end)-date.fromisoformat(start)).days>31:
        raise ValueError('El mapa admite un mes por consulta; elegí una celda del calendario.')
    condition,params=where(start,end,bbox)
    if metric=='modis':condition+=" AND sensor='MODIS'"
    if metric in ('viirs','calibrated'):condition+=" AND sensor='VIIRS'"
    with connection() as db:
        cursor=db.execute(f'''WITH g AS (
          SELECT floor(grid_col/5) cx,floor(grid_row/5) cy,avg(latitude) lat,avg(longitude) lon,
          count(*) n,count(*) FILTER(WHERE sensor='MODIS') modis,count(*) FILTER(WHERE sensor='VIIRS') viirs,
          max(frp_mw) frp_max_mw FROM detections WHERE {condition} GROUP BY 1,2)
          SELECT lat,lon,n,modis,viirs,frp_max_mw,count(*) OVER() total_groups,sum(n) OVER() total_detections
          FROM g ORDER BY n DESC,cx,cy LIMIT 6000''',params)
        names=[d[0] for d in cursor.description];records=[dict(zip(names,r)) for r in cursor.fetchall()]
    total_groups=records[0]['total_groups'] if records else 0
    total=records[0]['total_detections'] if records else 0
    points=[{k:v for k,v in r.items() if k not in ('total_groups','total_detections')} for r in records]
    return {'points':points,'total_groups':total_groups,'shown_groups':len(points),'total_detections':total,'shown_detections':sum(r['n'] for r in points),'aggregation_km':5,'from':start,'to':end,'metric':metric,'bbox':bbox}


def metadata():
    quality=json.loads((DATA/'resultados/control_calidad.json').read_text())
    calibration=json.loads((DATA/'calibracion/resumen_calibracion.json').read_text())['modis']
    return {'first_date':FIRST,'last_date':LAST,'viirs_first':VIIRS_FIRST,'rows':quality['output_rows'],'selected_rows':quality['selected_vegetation_rows'],'calibration':calibration,'bounds':[-73.1795,-54.9,-53.66204,-21.80942]}


class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ROOT/'dist'),**kwargs)

    def send(self,value,status=200,content_type='application/json; charset=utf-8',filename=None):
        payload=json.dumps(value,ensure_ascii=False,allow_nan=False).encode() if content_type.startswith('application/json') else value.encode()
        self.send_response(status);self.send_header('Content-Type',content_type)
        self.send_header('Content-Length',str(len(payload)));self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        if filename:self.send_header('Content-Disposition',f'attachment; filename="{filename}"')
        self.end_headers();self.wfile.write(payload)

    def do_GET(self):
        path=urlparse(self.path)
        if path.path.startswith('/api/'):
            try:
                if path.path=='/api/health':return self.send({'ok':True,'mode':'local'})
                if path.path=='/api/meta':return self.send(metadata())
                args=arguments(parse_qs(path.query))
                if path.path=='/api/calendar':return self.send(calendar(*args))
                if path.path=='/api/map':return self.send(map_data(*args))
                if path.path=='/api/export':
                    report=calendar(*args); stream=io.StringIO()
                    fields=['date_utc','active_cells_modis','active_cells_viirs','active_cells_union','modis_detections','viirs_detections','modis_equivalent_cells','modis_pi90_low','modis_pi90_high','eligible_modis','viirs_input_usable','observation_coverage_known']
                    writer=csv.DictWriter(stream,fieldnames=fields,extrasaction='ignore');writer.writeheader();writer.writerows(report['daily'])
                    return self.send(stream.getvalue(),content_type='text/csv; charset=utf-8',filename='chronosfire-datos-diarios.csv')
                return self.send({'error':'Ruta inexistente'},404)
            except (ValueError,TypeError) as e:return self.send({'error':str(e)},400)
            except (BrokenPipeError,ConnectionResetError):return
            except Exception as e:
                print(type(e).__name__,str(e),flush=True)
                return self.send({'error':'No se pudo consultar el archivo local. Revisá CHRONOS_DATA_DIR y el registro del servidor.'},500)
        return super().do_GET()


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8765);args=parser.parse_args()
    if not (DATA/'resultados/vegetacion_nominal_alta.parquet').is_file():raise SystemExit(f'No se encontraron los datos en {DATA}')
    source_daily()
    print(f'ChronosFire local: http://127.0.0.1:{args.port} · {DATA}',flush=True)
    ThreadingHTTPServer(('127.0.0.1',args.port),Handler).serve_forever()

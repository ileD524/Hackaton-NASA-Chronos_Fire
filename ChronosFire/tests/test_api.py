import csv
import io
import json
import os
import sys
import unittest
from pathlib import Path
from urllib.request import urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'server'))
import api
BASE=os.environ.get('CHRONOS_TEST_URL','http://127.0.0.1:8765')

def get(route,**args):
    with urlopen(BASE+route+'?'+urlencode(args),timeout=30) as r:return json.load(r)

class DataTests(unittest.TestCase):
    def test_calendar_matches_independent_parquet_count(self):
        j=get('/api/calendar',**{'from':'2020-08-01','to':'2020-08-31','metric':'modis'})
        with api.connection() as db:
            expected=db.execute("SELECT count(DISTINCT (date_utc,cell_id)) FROM detections WHERE sensor='MODIS' AND date_utc BETWEEN '2020-08-01' AND '2020-08-31'").fetchone()[0]
        self.assertEqual(j['months'][0]['value'],expected)
        self.assertEqual(expected,27856)
    def test_bbox_calendar_matches_raw_subset(self):
        args={'from':'2022-02-01','to':'2022-02-28','metric':'viirs','bbox':'-60,-30.8,-55.5,-27'}
        j=get('/api/calendar',**args)
        with api.connection() as db:
            expected=db.execute("SELECT count(DISTINCT (date_utc,cell_id)) FROM detections WHERE sensor='VIIRS' AND date_utc BETWEEN '2022-02-01' AND '2022-02-28' AND longitude BETWEEN -60 AND -55.5 AND latitude BETWEEN -30.8 AND -27").fetchone()[0]
        self.assertEqual(j['months'][0]['value'],expected)
        self.assertIsNone(j['daily'][0]['modis_equivalent_cells'])
    def test_calibrated_series_matches_original_daily_file(self):
        j=get('/api/calendar',**{'from':'2024-01-01','to':'2024-12-31','metric':'calibrated'})
        expected=sum(r['modis_equivalent_cells'] or 0 for day,r in api.source_daily().items() if day.startswith('2024-'))
        self.assertAlmostEqual(sum(m['value'] or 0 for m in j['months']),expected)
        self.assertTrue(any(m['partial'] for m in j['months']))
    def test_missing_is_distinct_from_zero(self):
        missing=get('/api/calendar',**{'from':'2003-01-01','to':'2003-01-31','metric':'viirs'})
        zero=get('/api/calendar',**{'from':'2025-01-01','to':'2025-01-31','metric':'viirs','bbox':'-50,-50,-49,-49'})
        self.assertIsNone(missing['months'][0]['value']);self.assertEqual(zero['months'][0]['value'],0)
    def test_invalid_requests_are_rejected(self):
        for args in [dict(metric='calibrated',bbox='-65,-35,-62,-29'),dict(metric='x'),{'from':'2026-01-01'},dict(bbox='1,2,0,3'),dict(bbox='nan,0,2,4'),{'from':'20200101'}]:
            with self.assertRaises(HTTPError) as e:get('/api/calendar',**args)
            self.assertEqual(e.exception.code,400)
    def test_map_accounts_for_truncation(self):
        j=get('/api/map',**{'from':'2020-08-01','to':'2020-08-31','metric':'modis'})
        self.assertEqual(j['total_detections'],30404)
        self.assertEqual(j['shown_groups'],len(j['points']))
        self.assertEqual(j['shown_detections'],sum(p['n'] for p in j['points']))
        self.assertGreaterEqual(j['total_groups'],j['shown_groups'])
    def test_export_is_real_daily_data(self):
        with urlopen(BASE+'/api/export?from=2020-08-01&to=2020-08-31&metric=modis') as r:
            self.assertIn('attachment',r.headers['Content-Disposition'])
            rows=list(csv.DictReader(io.StringIO(r.read().decode())))
        self.assertEqual(len(rows),31)
        self.assertEqual(sum(float(r['active_cells_modis']) for r in rows),27856)

if __name__=='__main__':unittest.main()

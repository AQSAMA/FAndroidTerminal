# SPDX-License-Identifier: GPL-3.0-only
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.request
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from publish_preview import StripCrossHostAuth, extract_artifact, validate_source

class PreviewPublisherTest(unittest.TestCase):
    def fixtures(self):
        sha = 'a' * 40
        run = {'id':10,'run_attempt':1,'run_number':3,'event':'pull_request','conclusion':'success','path':'.github/workflows/ci.yml','head_repository':{'full_name':'owner/repo'},'head_sha':sha,'pull_requests':[{'number':2}]}
        meta = {'repository':'owner/repo','run_id':10,'run_attempt':1,'pr':2,'commit':sha,'version_code':1003}
        pr = {'number':2,'state':'open','head':{'sha':sha,'repo':{'full_name':'owner/repo'}}}
        return run,meta,pr

    def test_only_current_same_repository_pr_can_be_signed(self):
        run,meta,pr = self.fixtures()
        validate_source(run,meta,pr,'owner/repo')
        for change in [('head',{'sha':'b'*40,'repo':{'full_name':'owner/repo'}}), ('state','closed')]:
            changed = {**pr, change[0]:change[1]}
            with self.assertRaises(ValueError): validate_source(run,meta,changed,'owner/repo')
        with self.assertRaises(ValueError): validate_source(run,{**meta,'version_code':1},pr,'owner/repo')
        with self.assertRaises(ValueError): validate_source({**run,'head_repository':{'full_name':'other/fork'}},meta,pr,'owner/repo')
        with self.assertRaises(ValueError): validate_source({**run,'path':'.github/workflows/other.yml'},meta,pr,'owner/repo')

    def archive(self, extra=None, corrupt=False):
        result = io.BytesIO()
        data = {'unsigned-preview.apk':b'preview','bootstrap-debug.apk':b'debug'}
        meta = {'sha256':{name:hashlib.sha256(value).hexdigest() for name,value in data.items()}}
        if corrupt: meta['sha256']['unsigned-preview.apk'] = '0'*64
        with zipfile.ZipFile(result,'w') as archive:
            for name,value in data.items(): archive.writestr(name,value)
            archive.writestr('build.json',json.dumps(meta))
            if extra: archive.writestr(extra,b'extra')
        return result.getvalue()

    def test_fixed_archive_contract_and_digests(self):
        with tempfile.TemporaryDirectory() as folder:
            extract_artifact(self.archive(),Path(folder))
            for payload in [self.archive('../escape'),self.archive('scripts/run.sh'),self.archive(corrupt=True)]:
                with self.assertRaises(ValueError): extract_artifact(payload,Path(folder))

    def test_cross_host_redirect_drops_authorization(self):
        handler = StripCrossHostAuth()
        request = urllib.request.Request('https://api.github.com/artifact',headers={'Authorization':'Bearer example'})
        redirected = handler.redirect_request(request,None,302,'Found',{},'https://objects.githubusercontent.com/file')
        self.assertIsNone(redirected.get_header('Authorization'))
        same_host = handler.redirect_request(request,None,302,'Found',{},'https://api.github.com/next')
        self.assertEqual('Bearer example',same_host.get_header('Authorization'))

if __name__ == '__main__': unittest.main()

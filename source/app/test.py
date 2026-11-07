import urllib.request
import json

data = json.dumps({'data': ['Hello test', 'expr-voice-5-m', 1.0]}).encode('utf-8')
req = urllib.request.Request('https://kingnish-kitten-tts.hf.space/gradio_api/call/predict', data=data, headers={'Content-Type': 'application/json'})

try:
    res = urllib.request.urlopen(req)
    print("Success:", res.read().decode('utf-8'))
except Exception as e:
    print("Error:", e)
    if hasattr(e, 'read'):
        print("Details:", e.read().decode('utf-8'))

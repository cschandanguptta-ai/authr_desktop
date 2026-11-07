import urllib.request
import json
import random
import string

def gen_hash():
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=11))

data = json.dumps({
    'fn_index': 2,
    'data': ['Hello, testing Gradio 5 queue join endpoint with Python!', 'expr-voice-5-m', 1.0],
    'session_hash': gen_hash()
}).encode('utf-8')

req = urllib.request.Request('https://kingnish-kitten-tts.hf.space/gradio_api/queue/join', data=data, headers={'Content-Type': 'application/json'})

try:
    res = urllib.request.urlopen(req)
    print("Success /gradio_api/queue/join:")
    # SSE stream response
    for line in res:
        line_str = line.decode('utf-8').strip()
        if line_str:
            print(line_str)
except Exception as e:
    print("Error /gradio_api/queue/join:", e)
    if hasattr(e, 'read'):
        print("Details:", e.read().decode('utf-8')[:1000])

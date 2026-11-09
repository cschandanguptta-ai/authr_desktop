import urllib.request
import json

data = json.dumps({
    'data': ['Hello, testing the direct api endpoint', 'expr-voice-5-m', 1.0]
}).encode('utf-8')

# Try /gradio_api/api/generate_speech
req = urllib.request.Request('https://kingnish-kitten-tts.hf.space/gradio_api/api/generate_speech', data=data, headers={'Content-Type': 'application/json'})

try:
    print("--- Testing /gradio_api/api/generate_speech ---")
    res = urllib.request.urlopen(req)
    print("Success:", res.read().decode('utf-8'))
except Exception as e:
    print("Failed /gradio_api/api/generate_speech:", e)
    if hasattr(e, 'read'):
        print("Details:", e.read().decode('utf-8')[:1000])

# Try /api/generate_speech
req2 = urllib.request.Request('https://kingnish-kitten-tts.hf.space/api/generate_speech', data=data, headers={'Content-Type': 'application/json'})

try:
    print("\n--- Testing /api/generate_speech ---")
    res2 = urllib.request.urlopen(req2)
    print("Success:", res2.read().decode('utf-8'))
except Exception as e:
    print("Failed /api/generate_speech:", e)
    if hasattr(e, 'read'):
        print("Details:", e.read().decode('utf-8')[:1000])

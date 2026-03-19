import sys
import traceback
import wave
import numpy as np
import soundfile as sf

if len(sys.argv) < 3:
    print("Usage: python convert_audio.py <input> <output>")
    sys.exit(1)

try:
    if sys.argv[1].lower().endswith('.wav'):
        with wave.open(sys.argv[1], 'rb') as w:
            nchannels = w.getnchannels()
            sampwidth = w.getsampwidth()
            framerate = w.getframerate()
            nframes = w.getnframes()
            raw_data = w.readframes(nframes)
            
            # Convert raw bytes to numpy array
            if sampwidth == 2:
                data = np.frombuffer(raw_data, dtype=np.int16).astype(np.float32) / 32768.0
            elif sampwidth == 1:
                data = (np.frombuffer(raw_data, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
            else:
                raise ValueError(f"Unsupported sample width: {sampwidth}")
            
            if nchannels > 1:
                data = data.reshape(-1, nchannels)
            
            samplerate = framerate
    else:
        data, samplerate = sf.read(sys.argv[1])
        data = data.astype(np.float32)
        
    sf.write(sys.argv[2], data, samplerate, format='OGG', subtype='VORBIS')
    print(f"Converted {sys.argv[1]} to {sys.argv[2]} successfully")
except Exception as e:
    print(f"Failed to convert {sys.argv[1]}")
    traceback.print_exc()
    sys.exit(1)


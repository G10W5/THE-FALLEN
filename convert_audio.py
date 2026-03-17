import soundfile as sf
data, samplerate = sf.read('World_enter.wav')
sf.write('src/main/resources/assets/thefallen/sounds/world_enter.ogg', data, samplerate, format='OGG', subtype='VORBIS')
print("Converted successfully")
